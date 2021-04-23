package com.objectcomputing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.util.logging.Slf4j

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.stream.Stream

@Slf4j
class BintrayService {

    static final String JFROG_ARTIFACTORY_BASE_URL = "https://repo.grails.org/grails"
    static final String REPO_KEY = 'bintray-grails-core-releases-local'
    static final String JFROG_REPO_URL = "$JFROG_ARTIFACTORY_BASE_URL/$REPO_KEY"
    static final String JFROG_ARTIFACT_SEARCH_URL = "$JFROG_ARTIFACTORY_BASE_URL/api/storage/$REPO_KEY"
    static final String BINTRAY_API_BASE_URL = "https://api.bintray.com"
    static final String DL_BINTRAY_BASE_URL = "https://dl.bintray.com"
    static final String CREDENTIALS = 'behlp:5M9$8cBtkPa^DE'
    static final String BINTRAY_API_CRED = 'puneetbehl:078dc67a6b712013e330d21030c5702b736a3a1c'
    static final String DATE_STRING = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYY_MM_dd"))
    static final String DOWNLOAD_PATH = "/Users/pbehl/Downloads/${DATE_STRING}"
    static final List<String> UPLOADED_PACKAGES = []
    static final String LOCAL_BINTRAY_PACKAGES_JSON_FILE_PATH = '/bintray-packages.json'
    static final List<String> VIRTUAL_REPOS = ['core']
    static final String DOWNLOAD_DIR_PATH = "/Users/pbehl/Downloads/$REPO_KEY-${LocalDate.now()}/"


    final ThreadPool threadPool = new ThreadPool(6, 8)

    PluginJsonReadService pluginJsonReadService = new PluginJsonReadService()

    @SuppressWarnings('GrMethodMayBeStatic')
    void downloadBintrayPackagesInfo(Integer startPos = 0, String repo, String toPath) {
        final String url = "$BINTRAY_API_BASE_URL/repos/grails/$repo/packages?start_pos=$startPos"
        def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $url -o $toPath".execute()
        if (downloadProc.waitFor() != 0) {
            log.error "Failed to download package information $url : \n" +
                    "$downloadProc.err.text"
        }
    }

    Stream<BintrayPackage> downloadCompletePackageInfo(String repo) {
        log.debug("Downloading complete package information")
        final ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        pluginJsonReadService.readBintraySimplePackages(LOCAL_BINTRAY_PACKAGES_JSON_FILE_PATH)
                .stream()
                .map(it -> {
                    BintrayPackage bintrayPackage = null
                    log.info("Downloading complete package information for $it.name")
                    final String url = "$BINTRAY_API_BASE_URL/packages/grails/${repo}/${it.name}"
                    def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $url".execute()
                    if (downloadProc.waitFor() != 0) {
                        log.error "Failed to download complete package information $url : \n" +
                                "$downloadProc.err.text"
                    } else {
                        final String response = downloadProc.text
                        bintrayPackage = objectMapper.readValue(response, BintrayPackage)
                    }
                    return bintrayPackage
                })
                .filter(it -> it != null)
    }

    void downloadAllBintrayPackagesInfo(String repo) {
        final ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT)
        final JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, BintrayPackageSimple)
        final List<BintrayPackageSimple> packages = []
        File downloadDir = new File("$DOWNLOAD_PATH/$repo")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        downloadBintrayPackagesInfo(0, repo, "$DOWNLOAD_PATH/$repo/${repo}.json")
        downloadBintrayPackagesInfo(50, repo, "$DOWNLOAD_PATH/$repo/$repo-50.json")
        downloadBintrayPackagesInfo(100, repo, "$DOWNLOAD_PATH/$repo/$repo-100.json")
        downloadBintrayPackagesInfo(150, repo, "$DOWNLOAD_PATH/$repo/$repo-150.json")
        downloadBintrayPackagesInfo(200, repo, "$DOWNLOAD_PATH/$repo/$repo-200.json")
        downloadBintrayPackagesInfo(250, repo, "$DOWNLOAD_PATH/$repo/$repo-250.json")
        objectMapper.getTypeFactory().constructCollectionType(List.class, BintrayPackageSimple)
        downloadDir.eachFileRecurse { File file ->
            List<BintrayPackageSimple> bintrayPackageSimpleList = objectMapper.readValue(file, type)
            packages.addAll(bintrayPackageSimpleList)
            file.delete()
        }
        downloadDir.deleteDir()
        final URL packagesFileUrl = getClass().getResource(LOCAL_BINTRAY_PACKAGES_JSON_FILE_PATH)
        objectMapper.writeValue(new File(packagesFileUrl.toURI()), packages)
    }


    void syncToArtifactory(String repo) {
        Stream<BintrayPackage> bintrayPackages = downloadCompletePackageInfo(repo)
        bintrayPackages
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
        if (packageFile.path.startsWith("property(class java/lang/String")) {
            log.debug "Bad path ${packageFile.path} - Skipping file ${packageFile.name}"
            return
        }
        threadPool.execute({ ->
            log.debug "Processing file ${packageFile.path}"

            File versionDir = new File(pkgDir, packageFile.version)
            final File localFile = new File(versionDir, packageFile.name)
            final String localPath = localFile.absolutePath

            String uploadFileName = packageFile.name.startsWith("org.grails.plugins:") ? packageFile.name.replaceAll('org.grails.plugins:', '') : packageFile.name
            String uploadPath = packageFile.path.contains("org.grails.plugins:") ? packageFile.path.replaceAll('org.grails.plugins:', '') : packageFile.path
            uploadFileName = uploadFileName.startsWith("org.grails:") ? uploadFileName.replaceAll('org.grails:', '') : uploadFileName
            uploadPath = uploadPath.contains("org.grails:") ? uploadPath.replaceAll('org.grails:', '') : uploadPath


            def verifyProc = "curl --no-alpn --http1.1 --fail -XGET -u $CREDENTIALS -L $JFROG_ARTIFACT_SEARCH_URL/$uploadPath".execute()

            if (verifyProc.waitFor() != 0) {
                if (!versionDir.exists()) {
                    versionDir.mkdirs()
                }
                if (!localFile.exists()) {
                    def downloadProc = "curl --no-alpn --http1.1 --fail -u $BINTRAY_API_CRED -L $DL_BINTRAY_BASE_URL/${packageFile.fileDownloadPath()} -o  $localPath".execute()
                    if (downloadProc.waitFor() != 0) {
                        for (String virtualRepo in VIRTUAL_REPOS) {
                            final String url = "$JFROG_ARTIFACTORY_BASE_URL/$virtualRepo/$uploadPath"
                            downloadProc = "curl --no-alpn --http1.1 --fail -u $CREDENTIALS -X GET -L $url -o $localPath".execute()
                            if (downloadProc.waitFor() != 0) {
                                log.error "Failed to download Bintray package file $url"
                                log.debug downloadProc.err.text
                            } else {
                                upload(uploadFileName, uploadPath, localPath, localFile)
                                break
                            }
                        }
                    } else {
                        upload(uploadFileName, uploadPath, localPath, localFile)
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
    private void fixChecksum(String uploadPath) {
        def fixChecksumProc = "curl --no-alpn --http1.1 --fail -X POST -u $CREDENTIALS -L $JFROG_ARTIFACTORY_BASE_URL/ui/checksums/fix -H Content-Type:application/json -d {\"repoKey\":\"$REPO_KEY\",\"path\":\"$uploadPath\"}".execute()
        if (fixChecksumProc.waitFor() != 0) {
            log.error("Error fixing checksum for $uploadPath: " + fixChecksumProc.err.text)
        }
        log.debug("Successfully fixed checksum inconsistency for $uploadPath")
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private void upload(String fileName, String filePath, String localPath, File localFile) {
        if (localFile.exists()) {
            log.debug "Uploading file ${fileName} from $localPath"
            def uploadProc = "curl --no-alpn --http1.1 --fail -u $CREDENTIALS -X PUT $JFROG_REPO_URL/$filePath -T $localPath".execute()
            if (uploadProc.waitFor() != 0) {
                log.error "Failed to upload $JFROG_REPO_URL/$filePath : \n$uploadProc.err.text"
            } else {
                fixChecksum(filePath)
                localFile.delete()
            }
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
