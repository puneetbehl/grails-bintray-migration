package com.objectcomputing

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.CompileStatic
import groovy.transform.ToString

@ToString
@CompileStatic
class BintrayPackageResponse {
    BintrayPackage bintrayPackage
    @JsonProperty("githubRepository.cloneUrl")
    Map githubRepository
    Date lastUpdated
    String readme
    String version
}
