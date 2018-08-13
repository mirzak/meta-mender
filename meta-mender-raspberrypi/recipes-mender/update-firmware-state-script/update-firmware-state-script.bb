DESCRIPTION = "Simple helloworld application"
SECTION = "examples"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

inherit mender-state-scripts

SRC_URI = "file://ArtifactReboot_Enter_50"

do_deploy() {
    cp ${WORKDIR}/ArtifactReboot_Enter_50 ${MENDER_STATE_SCRIPTS_DIR}/ArtifactReboot_Enter_50
}
