package com.objectcomputing

class Application {
    static void main(String[] args) {
        final BintrayService bintrayService = new BintrayService()

//        new CsvService().loadPluginsToCsv("/Users/pbehl/Downloads/${System.currentTimeMillis()}-grails-plugins.csv")

        final String repo = 'grails-core'
        bintrayService.downloadAllBintrayPackagesInfo(repo)
        bintrayService.syncToArtifactory(repo)
        println "Finished >>>>>>>>"
    }
}
