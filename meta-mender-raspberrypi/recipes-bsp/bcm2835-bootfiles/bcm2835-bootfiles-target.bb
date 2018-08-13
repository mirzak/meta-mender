DESCRIPTION = "Closed source binary files to help boot the ARM on the BCM2835."
LICENSE = "Proprietary"

LIC_FILES_CHKSUM = "file://LICENCE.broadcom;md5=4a4d169737c0786fb9482bb6d30401d1"

include recipes-bsp/common/firmware.inc

INHIBIT_DEFAULT_DEPS = "1"

DEPENDS = "update-firmware-state-script"

COMPATIBLE_MACHINE = "^rpi$"

S = "${RPIFW_S}/boot"

TARGET_DIR="${D}/boot/bcm2835-bootfiles"

do_install() {
    bbwarn "*******************************************************************************"
    bbwarn "WARNING!"
    bbwarn ""
    bbwarn "You are including Raspberry Pi boot firmware update in your artifact."
    bbwarn "This procedure might render your device unusable if interrupted."
    bbwarn ""
    bbwarn "You have been warned!"
    bbwarn "*******************************************************************************"

    install -d ${TARGET_DIR}

    for i in ${S}/*.elf ; do
        cp $i ${TARGET_DIR}
    done
    for i in ${S}/*.dat ; do
        cp $i ${TARGET_DIR}
    done
    for i in ${S}/*.bin ; do
        cp $i ${TARGET_DIR}
    done

    # Add stamp in deploy directory
    touch ${TARGET_DIR}/${PN}-${PV}.stamp
}

FILES_${PN} += "/boot/bcm2835-bootfiles"

INSANE_SKIP_${PN} += "arch"
