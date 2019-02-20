FILESEXTRAPATHS_prepend_rpi := "${THISDIR}/patches:"

BOOTENV_SIZE_rpi ?= "0x4000"

SRC_URI_append_rpi = " \
    file://0001-CONFIGS-rpi-enable-mender-requirements.patch \
    file://0001-speed-up-boot.patch \
"
