package com.objectcomputing


import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CsvService {

    void loadPluginsToCsv(String filePath) {
        List<BintrayPackage> bintrayPackages = new PluginJsonReadService().readPlugins('/grails-plugins.json')*.bintrayPackage
        CsvMapper csvMapper = new CsvMapper()
        CsvSchema csvSchema = csvMapper
                .schemaFor(BintrayPackage.class)
                .withHeader()
        Path csvFile = Files.createFile(Paths.get(filePath))
        csvMapper.writerFor(BintrayPackage.class)
                .with(csvSchema)
                .writeValues(csvFile.toFile())
                .writeAll(bintrayPackages)
    }
}
