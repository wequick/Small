<?xml version="1.0"?>
<globals>
    <globals file="../common/common_globals.xml.ftl" />
    <global id="topOut" value="." />
    <global id="assetsOut" value="./app/src/main/assets" />
    <global id="mavenUrl" value="mavenCentral" />
    <global id="copyGradleSupported" type="boolean" value="${(compareVersions(gradlePluginVersion, '2.0') < 0)?string}" />
    <global id="smallPluginVersion" value="1.0.0-beta5" />
    <global id="smallAarVersion" value="1.1.0-beta3" />
</globals>
