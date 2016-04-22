<?xml version="1.0"?>
<globals>
    <globals file="../common/common_globals.xml.ftl" />
    <global id="topOut" value="." />
    <global id="assetsOut" value="./app/src/main/assets" />
    <global id="mavenUrl" value="mavenCentral" />
    <global id="copyGradleSupported" type="boolean" value="${(compareVersions(gradlePluginVersion, '2.0') < 0)?string}" />
    <global id="smallPluginVersion" value="0.6.0" />
    <global id="smallAarVersion" value="0.9.0" />
</globals>
