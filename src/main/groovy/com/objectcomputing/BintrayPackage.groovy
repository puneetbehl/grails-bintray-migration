package com.objectcomputing

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import groovy.transform.CompileStatic

@CompileStatic
@JsonPropertyOrder(['name', 'repo', 'owner', 'desc', 'labels', 'attribute_names', 'licenses', 'custom_licenses', 'followers_count', 'created', 'website_url', 'issue_tracker_url',
        'linked_to_repos', 'permissions', 'versions', 'latest_version', 'updated', 'rating_count', 'system_ids', 'vcs_url', 'maturity'])
class BintrayPackage {
    String name
    String repo
    String owner
    String desc
    List<String> labels
    List<String> attribute_names
    List<String> licenses
    List<String> custom_licenses
    Integer followers_count
    String created
    String website_url
    String issue_tracker_url
    List<String> linked_to_repos
    List<String> permissions
    List<String> versions
    String latest_version
    String updated
    Integer rating_count
    List<String> system_ids
    String vcs_url
    String maturity

    String path() {
        "$owner/$repo"
    }

    String mavenFilePath(String systemId) {
        final pathPrefix = systemId.replaceAll('[.:]', '/')
        "$pathPrefix/maven-metadata.xml"
    }

    String filesPath(String version) {
        "packages/$owner/$repo/$name/versions/$version/files?include_unpublished=0"
    }
}
