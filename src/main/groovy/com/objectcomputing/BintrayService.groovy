package com.objectcomputing


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.util.logging.Slf4j

import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Slf4j
class BintrayService {

    static final String ARTIFACTORY_BASE_URL = "https://repo.grails.org/grails/bintray-grails-profiles-releases-local"
    static final String ARTIFACTORY_SEARCH_URL = "https://repo.grails.org/grails/api/storage/bintray-grails-profiles-releases-local"
    static final String BINTRAY_API_BASE_URL = "https://api.bintray.com"
    static final String DL_BINTRAY_BASE_URL = "https://dl.bintray.com"
    static final String CREDENTIALS = 'behlp:5M9$8cBtkPa^DE'
    static final String BINTRAY_API_CRED = 'puneetbehl:078dc67a6b712013e330d21030c5702b736a3a1c'
    static final String DATE_STRING = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYY_MM_dd"))
    static final String DOWNLOAD_PATH = "/Users/pbehl/Downloads/${DATE_STRING}"
    static final List<String> UPLOADED_PACKAGES = []
    static final String LOCAL_BINTRAY_PACKAGES_JSON_FILE_PATH = '/bintray-grails-profiles-packages.json'
    static final List<String> VIRTUAL_REPOS = ['core']
    static final String DOWNLOAD_DIR_PATH = "/Users/pbehl/Downloads/bintray-grails-profile-${LocalDate.now()}/"


    ThreadPool threadPool = new ThreadPool(6, 14)

    PluginJsonReadService pluginJsonReadService = new PluginJsonReadService()

    @SuppressWarnings('GrMethodMayBeStatic')
    void downloadBintrayPackagesInfo(Integer startPos = 0, String repo, String toPath) {
        final String url = "$BINTRAY_API_BASE_URL/repos/grails/$repo/packages?start_pos=$startPos"
        def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $url -o $toPath".execute()
        if (downloadProc.waitFor() != 0) {
            log.error "Failed to download $url : \n" +
                    "$downloadProc.err.text"
        }
    }

    List<BintrayPackage> downloadCompletePackageInfo(String repo) {
        final ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        pluginJsonReadService.readBintraySimplePackages(LOCAL_BINTRAY_PACKAGES_JSON_FILE_PATH)
                .collect({
                    final String url = "$BINTRAY_API_BASE_URL/packages/grails/${repo}/${it.name}"
                    def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $url".execute()
                    if (downloadProc.waitFor() != 0) {
                        log.error "Failed to download $url : \n" +
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
        final ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT)
        final JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, BintrayPackageSimple)
        final List<BintrayPackageSimple> packages = []
        File downloadDir = new File("$DOWNLOAD_PATH/$name")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        downloadBintrayPackagesInfo(0, repo, "$DOWNLOAD_PATH/$name/${name}.json")
        downloadBintrayPackagesInfo(50, repo, "$DOWNLOAD_PATH/$name/$name-50.json")
        downloadBintrayPackagesInfo(100, repo, "$DOWNLOAD_PATH/$name/$name-100.json")
        downloadBintrayPackagesInfo(150, repo, "$DOWNLOAD_PATH/$name/$name-150.json")
        downloadBintrayPackagesInfo(200, repo, "$DOWNLOAD_PATH/$name/$name-200.json")
        downloadBintrayPackagesInfo(250, repo, "$DOWNLOAD_PATH/$name/$name-250.json")
        objectMapper.getTypeFactory().constructCollectionType(List.class, BintrayPackageSimple)
        downloadDir.eachFileRecurse { File file ->
            List<BintrayPackageSimple> bintrayPackageSimpleList = objectMapper.readValue(file, type)
            packages.addAll(bintrayPackageSimpleList)
            file.delete()
        }
        objectMapper.writeValue(new File(downloadDir, "$name-merged.json"), packages)
    }


    void syncPluginsToArtifactory(String repo) {
        List<BintrayPackage> bintrayPackages = downloadCompletePackageInfo(repo)
        bintrayPackages.stream()
                .sorted(Comparator.comparing(BintrayPackage::getName))
                .filter({ !(it.name in UPLOADED_PACKAGES) })
                .forEach({ BintrayPackage pkg ->

                    File pkgDir = localDownloadDir(pkg.name)

                    threadPool.execute({ ->
                        log.info "Processing package: $pkg.name"
                    })

                    pkg.system_ids.each { systemId ->
                        final BintrayPackageFile metadata = new BintrayPackageFile(name: "maven-metadata.xml", path: pkg.mavenFilePath(systemId), owner: pkg.owner, repo: pkg.repo, version: "")
                        processFile(metadata, pkgDir)
                    }

                    pkg.versions.forEach({ version ->
                        List<BintrayPackageFile> packageFiles = readPackageFiles(version, pkg)
                        packageFiles.each {
                            file -> processFile(file, pkgDir)
                        }
                        threadPool.waitUntilAllTasksFinished()
                    })
                })
        threadPool.waitUntilAllTasksFinished()
        threadPool.stop()
    }

    void processFile(BintrayPackageFile packageFile, File pkgDir) {
        if (packageFile.name.endsWith('.md5')) {
            log.debug "Skipping file ${packageFile.name}"
            return
        }
        threadPool.execute({ ->
            log.debug "Processing file ${packageFile.path}"

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
                        for (String virtualRepo in VIRTUAL_REPOS) {
                            downloadProc = "curl --no-alpn --http1.1 --fail -u $CREDENTIALS -X GET -L https://repo.grails.org/grails/$virtualRepo/$packageFile.path -o $localPath".execute()
                            if (downloadProc.waitFor() != 0) {
                                log.error "Failed to download https://repo.grails.org/grails/$virtualRepo/$packageFile.path"
                                log.debug downloadProc.err.text
                            } else {
                                log.debug "Download successful from https://repo.grails.org/grails/$virtualRepo/$packageFile.path"
                                upload(packageFile, localPath, localFile)
                                break
                            }
                        }
                    } else {
                        upload(packageFile, localPath, localFile)
                    }
                }
            } else {
                log.debug "File $packageFile.path already exists"
            }
        })
    }

    List<BintrayPackageFile> readPackageFiles(String version, BintrayPackage bintrayPackage) {
        URL url = new URL("$BINTRAY_API_BASE_URL/${bintrayPackage.filesPath(version)}")
        pluginJsonReadService.readPackageFiles(url)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private void upload(BintrayPackageFile packageFile, String localPath, File localFile) {
        log.debug "Uploading file ${packageFile.name} from $localPath"
        def uploadProc = "curl --no-alpn --http1.1 --fail -u $CREDENTIALS -X PUT $ARTIFACTORY_BASE_URL/$packageFile.path -T $localPath".execute()
        if (uploadProc.waitFor() != 0) {
            log.error "Failed to upload $ARTIFACTORY_BASE_URL/$packageFile.path : \n$uploadProc.err.text"
        } else {
            localFile.delete()
        }
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private File localDownloadDir(String packageName) {
        File pkgDir = new File("$DOWNLOAD_DIR_PATH/$packageName")
        if (!pkgDir.exists()) {
            pkgDir.mkdirs()
        }
        pkgDir
    }
}
