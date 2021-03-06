IMAGE_FSTYPES_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-image', ' mender mender.bmap', '', d)}"
IMAGE_FSTYPES_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-image-sd', ' sdimg sdimg.bmap', '', d)}"
IMAGE_FSTYPES_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-image-ubi', ' ubimg ubimg.bmap', '', d)}"

python() {
    depends_on_mender_image = ['mender-image-sd', 'mender-image-ubi']

    features = d.getVar('DISTRO_FEATURES').split()
    for dep in depends_on_mender_image:
        if dep in features and 'mender-image' not in features:
            bb.fatal("If enabling %s in MENDER_FEATURES_ENABLE or DISTRO_FEATURES, mender-image must also be enabled"
                     % dep)
}

# Update fstab for Mender
ROOTFS_POSTPROCESS_COMMAND_append += " mender_update_fstab_file ; "
mender_update_fstab_file() {
    mkdir -p ${IMAGE_ROOTFS}/uboot
    mkdir -p ${IMAGE_ROOTFS}/data

    echo "# Where the U-Boot environment resides; for devices with SD card support ONLY!" >> ${IMAGE_ROOTFS}${sysconfdir}/fstab

    if [ -n "${MENDER_BOOT_PART}" ]; then
        echo "${MENDER_BOOT_PART}   /uboot               ${MENDER_BOOT_PART_FSTYPE}       defaults,sync    0  0" >> ${IMAGE_ROOTFS}${sysconfdir}/fstab
    else
        bbdebug 2 "MENDER_BOOT_PART not set. Not adding to fstab..."
    fi

    echo "${MENDER_DATA_PART}   /data                ${MENDER_DATA_PART_FSTYPE}       defaults         0  0" >> ${IMAGE_ROOTFS}${sysconfdir}/fstab
}

# Setup state script version file.
MENDER_STATE_SCRIPTS_VERSION = "2"
ROOTFS_POSTPROCESS_COMMAND_append += "${@bb.utils.contains('DISTRO_FEATURES', 'mender-image', ' mender_create_scripts_version_file ; ', '', d)}"

mender_create_scripts_version_file() {
    install -d -m 755 ${IMAGE_ROOTFS}${sysconfdir}/mender/scripts/
    echo -n "${MENDER_STATE_SCRIPTS_VERSION}" > ${IMAGE_ROOTFS}${sysconfdir}/mender/scripts/version
}
