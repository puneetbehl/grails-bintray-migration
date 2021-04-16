package com.objectcomputing

class Application {

    String getGreeting() {
        return 'Hello World!'
    }

    static void main(String[] args) {
        final BintrayService bintrayService = new BintrayService()

//        new CsvService().loadPluginsToCsv("/Users/pbehl/Downloads/${System.currentTimeMillis()}-grails-plugins.csv")

//        bintrayService.downloadAllBintrayPackagesInfo('plugins', 'grails-plugins-packages')
//        bintrayService.downloadAllBintrayPackagesInfo('grails-core', 'bintray-grails-core-packages')
        bintrayService.syncPluginsToArtifactory()
        println "Finished >>>>>>>>"
    }
}
