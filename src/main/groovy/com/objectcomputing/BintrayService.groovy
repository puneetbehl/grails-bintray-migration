package com.objectcomputing


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BintrayService {

    static final String ARTIFACTORY_BASE_URL = "https://repo.grails.org/grails/bintray-grails-core-releases-backup-local"
    static final String ARTIFACTORY_SEARCH_URL = "https://repo.grails.org/grails/api/storage/bintray-grails-core-releases-backup-local"
    static final String BINTRAY_API_BASE_URL = "https://api.bintray.com"
    static final String DL_BINTRAY_BASE_URL = "https://dl.bintray.com"
    static final String CREDENTIALS = 'behlp:5M9$8cBtkPa^DE'
    static final String BINTRAY_API_CRED = 'puneetbehl:078dc67a6b712013e330d21030c5702b736a3a1c'
    static final String DATE_STRING = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYY_MM_dd"))
    static final String DOWNLOAD_PATH = "/Users/pbehl/Downloads/${DATE_STRING}"
    static final List<String> UPLOADED_PACKAGES = []


    ThreadPool threadPool = new ThreadPool(14, 14);

    PluginJsonReadService pluginJsonReadService = new PluginJsonReadService()

    void downloadBintrayPackagesInfo(Integer startPos = 0, String repo, String toPath) {
        final String url = "$BINTRAY_API_BASE_URL/repos/grails/$repo/packages?start_pos=$startPos"
        def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $url -o $toPath".execute()
        if (downloadProc.waitFor() != 0) {
            println "Failed to download $url : \n" +
                    "$downloadProc.err.text"
        }
    }

    List<BintrayPackage> downloadCompletePackageInfo(String repo) {
        final ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        pluginJsonReadService.readBintraySimplePackages('/bintray-grails-core-packages.json')
                .collect({
                    final String url = "$BINTRAY_API_BASE_URL/packages/grails/${repo}/${it.name}"
                    def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $url".execute()
                    if (downloadProc.waitFor() != 0) {
                        println "Failed to download $url : \n" +
                                "$downloadProc.err.text"
                    } else {
                        final String response = downloadProc.text
                        final BintrayPackage bintrayPackage = objectMapper.readValue(response, BintrayPackage)
                        return bintrayPackage
                    }
                    return null
                }).findAll({ it })

    }

    void downloadAllBintrayPackagesInfo(String repo, String name) {
        File downloadDir = new File("$DOWNLOAD_PATH/$name")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        downloadBintrayPackagesInfo(0, repo, "$DOWNLOAD_PATH/$name/${name}.json")
        downloadBintrayPackagesInfo(51, repo, "$DOWNLOAD_PATH/$name/$name-51.json")
    }


    void syncPluginsToArtifactory() {
//        List<BintrayPackage> bintrayPackages = pluginJsonReadService.readPlugins('/grails-plugins.json')*.bintrayPackage
        List<BintrayPackage> bintrayPackages = downloadCompletePackageInfo("grails-core")
        bintrayPackages.stream()
                .sorted(Comparator.comparing(BintrayPackage::getName))
                .filter({ !(it.name in UPLOADED_PACKAGES) })
                .forEach({ BintrayPackage pkg ->

                    File pkgDir = localDownloadDir(pkg.name)

                    threadPool.execute({ ->
                        println "Processing package: $pkg.name"
                    })

                    pkg.system_ids.each { systemId ->
                        final BintrayPackageFile metadata = new BintrayPackageFile(name: "maven-metadata.xml", path: pkg.mavenFilePath(systemId), owner: pkg.owner, repo: pkg.repo, version: "")
                        processFile(metadata, pkgDir)
                    }

                    pkg.versions.forEach({ version ->
                        List<BintrayPackageFile> packageFiles = readPackageFiles(version, pkg)
                        packageFiles.each { file -> processFile(file, pkgDir) }
                        threadPool.waitUntilAllTasksFinished()
                    })
                })
        threadPool.waitUntilAllTasksFinished()
        threadPool.stop()
    }

    private void processFile(BintrayPackageFile packageFile, File pkgDir) {
        if (packageFile.name.endsWith('MD5')) {
            println "Skipping file ${packageFile.name}"
            return
        }
        threadPool.execute({ ->
            File versionDir = new File(pkgDir, packageFile.version)
            final File localFile = new File(versionDir, packageFile.name)
            final String localPath = localFile.absolutePath

            def verifyProc = "curl --no-alpn --http1.1 --fail -XGET -u $CREDENTIALS -L $ARTIFACTORY_SEARCH_URL/$packageFile.path".execute()

            if (verifyProc.waitFor() != 0) {
                if (!versionDir.exists()) {
                    versionDir.mkdirs()
                }
                if (!localFile.exists()) {
                    def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $DL_BINTRAY_BASE_URL/${packageFile.fileDownloadPath()} -o  $localPath".execute()
                    if (downloadProc.waitFor() != 0) {
//                    println "Failed to download $DL_BINTRAY_BASE_URL/${packageFile.fileDownloadPath()} : $downloadProc.err.text"

                        downloadProc = "curl --no-alpn --http1.1 --fail -u $CREDENTIALS -X GET -L https://repo.grails.org/grails/core/$packageFile.path -o $localPath".execute()

                        if (downloadProc.waitFor() != 0) {
                            println "Failed to download https://repo.grails.org/grails/core/$packageFile.path : $downloadProc.err.text"
                        } else {
                            upload(packageFile, localPath, localFile)
                        }
                    } else {
                        upload(packageFile, localPath, localFile)
                    }
                }
            } else {
                println "File $packageFile.path already exists"
            }
        })
    }

    private void upload(BintrayPackageFile packageFile, String localPath, File localFile) {
        def uploadProc = "curl --no-alpn --http1.1 --fail -u $CREDENTIALS -X PUT $ARTIFACTORY_BASE_URL/$packageFile.path -T $localPath".execute()
        if (uploadProc.waitFor() != 0) {
            println "Failed to upload $ARTIFACTORY_BASE_URL/$packageFile.path : \n$uploadProc.err.text"
        } else {
            localFile.delete()
        }
    }

    List<BintrayPackageFile> readPackageFiles(String version, BintrayPackage bintrayPackage) {
        URL url = new URL("$BINTRAY_API_BASE_URL/${bintrayPackage.filesPath(version)}")
        pluginJsonReadService.readPackageFiles(url)
    }

    File localDownloadDir(String packageName) {
        File pkgDir = new File("/Users/pbehl/Downloads/bintray-grails-core-${LocalDate.now()}/$packageName")
        if (!pkgDir.exists()) {
            pkgDir.mkdirs()
        }
        pkgDir
    }
}
