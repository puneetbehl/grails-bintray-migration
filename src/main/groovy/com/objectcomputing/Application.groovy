package com.objectcomputing

class Application {
    static void main(String[] args) {
        final BintrayService bintrayService = new BintrayService()

//        new CsvService().loadPluginsToCsv("/Users/pbehl/Downloads/${System.currentTimeMillis()}-grails-plugins.csv")

//        bintrayService.downloadAllBintrayPackagesInfo('plugins', 'grails-plugins-packages')
//        bintrayService.downloadAllBintrayPackagesInfo('profiles', 'bintray-grails-profiles-packages')
        bintrayService.syncPluginsToArtifactory("profiles")
        println "Finished >>>>>>>>"
    }
}
