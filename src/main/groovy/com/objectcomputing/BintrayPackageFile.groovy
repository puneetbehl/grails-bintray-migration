package com.objectcomputing

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import groovy.transform.CompileStatic
import groovy.transform.ToString


@ToString
@CompileStatic
@JsonPropertyOrder(["name", "owner", "repo", "path", "version"])
class BintrayPackageFile {

    String name
    String path
    String owner
    String repo
    String version

    String fileDownloadPath() {
        "$owner/$repo/$path"
    }
}
