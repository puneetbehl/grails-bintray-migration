package com.objectcomputing

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

class PluginJsonReadService {

    static final String FILE_NAME = 'grails-plugins.json'

    List<BintrayPackageResponse> readPlugins(String path) {
        URL url = getClass().getResource(path)
        new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(url, new TypeReference<ArrayList<BintrayPackageResponse>>() {})
    }

    List<BintrayPackageSimple> readBintraySimplePackages(String path) {
        URL url = getClass().getResource(path)
        new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(url, new TypeReference<ArrayList<BintrayPackageSimple>>() {})
    }

    List<BintrayPackageFile> readPackageFiles(URL url) {
        new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(url, new TypeReference<ArrayList<BintrayPackageFile>>() {})
    }

    List<BintrayPackage> readBintrayPackages(String fromPath) {
        URL url = getClass().getResource(fromPath)
        new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(url, new TypeReference<ArrayList<BintrayPackage>>() {})
    }

}
