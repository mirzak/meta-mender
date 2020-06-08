################################################################################
#-------------------------------------------------------------------------------
# THINGS TO CONSIDER FOR EACH RELEASE:
# - SRC_URI (particularly "branch")
# - SRCREV
# - DEFAULT_PREFERENCE
#-------------------------------------------------------------------------------

SRC_URI = "git://github.com/mendersoftware/mender;protocol=https;branch=2.2.x"

# Tag: 2.2.0
SRCREV = "44753ca67caba0deea203a7b9d7785c71a0c05b4"

# Enable this in Betas, not in finals.
# Downprioritize this recipe in version selections.
#DEFAULT_PREFERENCE = "-1"

################################################################################

# DO NOT change the checksum here without make sure that ALL licenses (including
# dependencies) are included in the LICENSE variable below.
LIC_FILES_CHKSUM = "file://src/github.com/mendersoftware/mender/LIC_FILES_CHKSUM.sha256;md5=80ba3790b689991e47685da401fd3375"
LICENSE = "Apache-2.0 & BSD-2-Clause & BSD-3-Clause & ISC & MIT & OLDAP-2.8"

DEPENDS += "xz"
RDEPENDS_${PN} += "liblzma"

# MEN-2948: systemd service is still named mender.service in 2.2.x
MENDER_CLIENT = "mender"


DESCRIPTION = "Mender tool for doing OTA software updates."
HOMEPAGE = "https://mender.io"

RDEPENDS_${PN}_append_mender-growfs-data_mender-systemd = " parted"

def cert_location_if_server_crt_in(src_uri, d):
    for src in src_uri.split():
        if src.endswith("/server.crt"):
            return "%s/mender/server.crt" % d.getVar('sysconfdir')
    return ""

MENDER_CLIENT ?= "mender-client"
MENDER_SERVER_URL ?= "https://docker.mender.io"
MENDER_CERT_LOCATION ??= "${@cert_location_if_server_crt_in('${SRC_URI}', d)}"
# Tenant token
MENDER_TENANT_TOKEN ?= "dummy"
SYSTEMD_AUTO_ENABLE ?= "enable"
MENDER_UPDATE_POLL_INTERVAL_SECONDS ?= "1800"
MENDER_INVENTORY_POLL_INTERVAL_SECONDS ?= "28800"
MENDER_RETRY_POLL_INTERVAL_SECONDS ?= "300"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

inherit go
inherit pkgconfig
inherit systemd

SYSTEMD_SERVICE_${PN} = "${MENDER_CLIENT}.service"
FILES_${PN} += "\
    ${datadir}/mender/identity \
    ${datadir}/mender/identity/mender-device-identity \
    ${datadir}/mender/inventory \
    ${datadir}/mender/inventory/mender-inventory-bootloader-integration \
    ${datadir}/mender/inventory/mender-inventory-hostinfo \
    ${datadir}/mender/inventory/mender-inventory-network \
    ${datadir}/mender/inventory/mender-inventory-os \
    ${datadir}/mender/inventory/mender-inventory-rootfs-type \
    ${datadir}/mender/modules/v3/deb \
    ${datadir}/mender/modules/v3/directory \
    ${datadir}/mender/modules/v3/docker \
    ${datadir}/mender/modules/v3/rpm \
    ${datadir}/mender/modules/v3/script \
    ${datadir}/mender/modules/v3/single-file \
    ${sysconfdir}/mender.conf \
    ${sysconfdir}/udev/mount.blacklist.d/mender \
    ${systemd_unitdir}/system/${MENDER_CLIENT}.service \
    /data/mender/device_type \
    /data/mender/mender.conf \
"

SYSROOT_DIRS += "/data"

SRC_URI_append_mender-image_mender-systemd = " \
    file://mender-client-data-dir.service \
"

SRC_URI_append_mender-persist-systemd-machine-id = " \
    file://mender-client-systemd-machine-id.service \
    file://mender-client-set-systemd-machine-id.sh \
"

SRC_URI_append_mender-growfs-data_mender-systemd = " \
    file://mender-client-resize-data-part.sh.in \
    file://mender-grow-data.service \
    file://mender-systemd-growfs-data.service \
"

FILES_${PN}_append_mender-image_mender-systemd = " \
    ${systemd_unitdir}/system/${MENDER_CLIENT}-data-dir.service \
    ${systemd_unitdir}/system/${MENDER_CLIENT}.service.wants/${MENDER_CLIENT}-data-dir.service \
"

FILES_${PN}_append_mender-growfs-data_mender-systemd = " \
    ${bindir}/mender-client-resize-data-part \
    ${systemd_unitdir}/system/mender-grow-data.service \
    ${systemd_unitdir}/system/mender-systemd-growfs-data.service \
    ${systemd_unitdir}/system/data.mount.wants/mender-grow-data.service \
    ${systemd_unitdir}/system/data.mount.wants/mender-systemd-growfs-data.service \
"

FILES_${PN}_append_mender-persist-systemd-machine-id = " \
    ${systemd_unitdir}/system/mender-systemd-machine-id.service \
    ${systemd_unitdir}/system/${MENDER_CLIENT}.service.wants/${MENDER_CLIENT}-systemd-machine-id.service \
    ${bindir}/${MENDER_CLIENT}-set-systemd-machine-id.sh \
"

# Go binaries produce unexpected effects that the Yocto QA mechanism doesn't
# like. We disable those checks here.
INSANE_SKIP_${PN} = "ldflags textrel"
INSANE_SKIP_${PN}-ptest = "ldflags textrel"

GO_IMPORT = "github.com/mendersoftware/mender"

PACKAGECONFIG_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-client-install', ' mender-client-install', '', d)}"
PACKAGECONFIG_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-uboot', ' u-boot', '', d)}"
PACKAGECONFIG_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-grub', ' grub', '', d)}"

PACKAGECONFIG[mender-client-install] = ",,,mender-artifact-info ca-certificates"
PACKAGECONFIG[u-boot] = ",,,u-boot-fw-utils"
PACKAGECONFIG[grub] = ",,,grub-editenv grub-mender-grubenv"
# The docker module depends on bash, and of course on docker. However, docker is
# a very large requirement, which we will not mandate. Bash however, we require,
# because otherwise the Yocto QA checks will complain.
PACKAGECONFIG[modules] = ",,,bash"

# NOTE: Splits the mender.conf file by default into a transient and a persistent config. Needs to be
# explicitly disabled if this is not to apply.
PACKAGECONFIG[split-mender-config] = ",,,"
PACKAGECONFIG_append = " split-mender-config"

do_compile() {
    GOPATH="${B}:${S}"
    export GOPATH
    PATH="${B}/bin:$PATH"
    export PATH

    DEFAULT_CERT_MD5="1fba17436027eb1f5ceff4af9a63c9c2"

    if [ "$(md5sum ${WORKDIR}/server.crt | awk '{ print $1 }')" = $DEFAULT_CERT_MD5 ]; then
        bbwarn "You are building with the default server certificate, which is not intended for production use"
    fi

    # mender is using vendored dependencies, any 3rd party libraries go to into
    # /vendor directory inside mender source tree. In order for `go build` to pick
    # up vendored deps from our source tree, the mender source tree itself must be
    # located inside $GOPATH/src/${GO_IMPORT}
    #
    # recreate temporary $GOPATH/src/${GO_IMPORT} structure and link our source tree
    mkdir -p ${B}/src/$(dirname ${GO_IMPORT})
    test -e ${B}/src/${GO_IMPORT} || ln -s ${S} ${B}/src/${GO_IMPORT}
    cd ${B}/src/${GO_IMPORT}

    # run verbose build, we should see which dependencies are pulled in
    oe_runmake V=1

    echo "device_type=${MENDER_DEVICE_TYPE}" > ${B}/device_type
}

python do_prepare_mender_conf() {
    import json

    # If a mender.conf has been provided in SRC_URI, merge this with the
    # settings we generate. The settings specified by variables take precedence.
    src_conf = os.path.join(d.getVar("WORKDIR"), "mender.conf")
    if os.path.exists(src_conf):
        bb.debug(1, "mender.conf already present in ${WORKDIR}, merging with generated settings.")
        fd = open(src_conf)
        transient_conf = json.load(fd)
        fd.close()
    else:
        bb.debug(1, "mender.conf not present in ${WORKDIR}, generating a new one.")
        transient_conf = {}
    def conf_maybe_add(key, value, getvar, integer):
        if getvar:
            warn_str = "variable '%s'" % value
            value = d.getVar(value)
        else:
            warn_str = "automatically provided settings"
        if value is not None and value != "":
            if transient_conf.get(key) is not None and transient_conf[key] != value:
                bb.warn("Configuration key '%s', found in mender.conf, conflicts with %s. Choosing the latter." % (key, warn_str))
            if integer:
                transient_conf[key] = int(value)
            else:
                transient_conf[key] = value

    key_in_src_uri = os.path.exists(os.path.join(d.getVar("WORKDIR"), "artifact-verify-key.pem"))
    key_in_var = d.getVar("MENDER_ARTIFACT_VERIFY_KEY") not in [None, ""]

    # Add new variable -> config assignments here.
    if key_in_src_uri or key_in_var:
        conf_maybe_add("ArtifactVerifyKey", "%s/mender/artifact-verify-key.pem" % d.getVar("sysconfdir"), getvar=False, integer=False)
    conf_maybe_add("InventoryPollIntervalSeconds", "MENDER_INVENTORY_POLL_INTERVAL_SECONDS", getvar=True, integer=True)
    # Mandatory variables - will always exist
    conf_maybe_add("RetryPollIntervalSeconds", "MENDER_RETRY_POLL_INTERVAL_SECONDS", getvar=True, integer=True)
    conf_maybe_add("RootfsPartA", "MENDER_ROOTFS_PART_A", getvar=True, integer=False)
    conf_maybe_add("RootfsPartB", "MENDER_ROOTFS_PART_B", getvar=True, integer=False)
    conf_maybe_add("ServerCertificate", "MENDER_CERT_LOCATION", getvar=True, integer=False)
    conf_maybe_add("ServerURL", "MENDER_SERVER_URL", getvar=True, integer=False)
    conf_maybe_add("UpdatePollIntervalSeconds", "MENDER_UPDATE_POLL_INTERVAL_SECONDS", getvar=True, integer=True)

    # Tenant-token is optional, but falls back to a default-value set in config.go
    conf_maybe_add("TenantToken", "MENDER_TENANT_TOKEN", getvar=True, integer=False)

    # Filter returns the variables that are present in both instances.
    # Thus no misspelled variables will ever enter the persistent configuration during migration.
    persistent_configs = bb.utils.filter("MENDER_PERSISTENT_CONFIGURATION_VARS", d.getVar("MENDER_CONFIGURATION_VARS"), d)

    persistent_conf = {}

    # Extract the variables that are destined for the persistent mender-configuration.
    if bb.utils.contains('PACKAGECONFIG', 'split-mender-config', True, False, d):
        for config_var in transient_conf:
            if config_var in persistent_configs:
                persistent_conf[config_var] = transient_conf[config_var]

        # Remove the configurations from the transient conf that are already in the persistent configuration.
        for config_var in persistent_conf:
            del transient_conf[config_var]

        dst_conf = os.path.join(d.getVar("B"), "persistent_mender.conf")
        fd = open(dst_conf, "w")
        json.dump(persistent_conf, fd, indent=4, sort_keys=True)
        fd.close()

    dst_conf = os.path.join(d.getVar("B"), "transient_mender.conf")
    fd = open(dst_conf, "w")
    json.dump(transient_conf, fd, indent=4, sort_keys=True)
    fd.close()

}
addtask do_prepare_mender_conf after do_compile before do_install
do_prepare_mender_conf[vardeps] = " \
    MENDER_ARTIFACT_VERIFY_KEY \
    MENDER_CERT_LOCATION \
    MENDER_INVENTORY_POLL_INTERVAL_SECONDS \
    MENDER_RETRY_POLL_INTERVAL_SECONDS \
    MENDER_ROOTFS_PART_A \
    MENDER_ROOTFS_PART_B \
    MENDER_SERVER_URL \
    MENDER_TENANT_TOKEN \
    MENDER_UPDATE_POLL_INTERVAL_SECONDS \
    MENDER_PERSISTENT_CONFIGURATION_VARS \
"

do_install() {
    oe_runmake \
        -C ${B}/src/${GO_IMPORT} \
        V=1 \
        prefix=${D} \
        bindir=${bindir} \
        datadir=${datadir} \
        sysconfdir=${sysconfdir} \
        systemd_unitdir=${systemd_unitdir} \
        install-bin \
        install-identity-scripts \
        install-inventory-scripts \
        install-systemd \
        ${@bb.utils.contains('PACKAGECONFIG', 'modules', 'install-modules', '', d)}

    #install our prepared configuration
    install -d ${D}/${sysconfdir}/mender
    install -d ${D}/data/mender
    if [ -f ${B}/transient_mender.conf ]; then
        install -m 0644 ${B}/transient_mender.conf ${D}/${sysconfdir}/mender/mender.conf
    fi
    if [ -f ${B}/persistent_mender.conf ]; then
        install -m 0644 ${B}/persistent_mender.conf ${D}/data/mender/mender.conf
    fi

    #install server certificate
    if [ -f ${WORKDIR}/server.crt ]; then
        install -m 0755 -d $(dirname ${D}${MENDER_CERT_LOCATION})
        install -m 0444 ${WORKDIR}/server.crt ${D}${MENDER_CERT_LOCATION}
    fi

    install -d ${D}/${localstatedir}/lib/mender

    # install artifact verification key, if any.
    if [ -e ${WORKDIR}/artifact-verify-key.pem ]; then
        if [ -n "${MENDER_ARTIFACT_VERIFY_KEY}" ]; then
            bbfatal "You can not specify both MENDER_ARTIFACT_VERIFY_KEY and have artifact-verify-key.pem in SRC_URI."
        fi
        install -m 0444 ${WORKDIR}/artifact-verify-key.pem ${D}${sysconfdir}/mender
    elif [ -n "${MENDER_ARTIFACT_VERIFY_KEY}" ]; then
        install -m 0444 "${MENDER_ARTIFACT_VERIFY_KEY}" ${D}${sysconfdir}/mender/artifact-verify-key.pem
    fi

    if ${@bb.utils.contains('DISTRO_FEATURES', 'mender-image', 'true', 'false', d)}; then
        # symlink /var/lib/mender to /data/mender
        rm -rf ${D}/${localstatedir}/lib/mender
        ln -s /data/mender ${D}/${localstatedir}/lib/mender

        install -m 755 -d ${D}/data/mender
        install -m 444 ${B}/device_type ${D}/data/mender/
    fi

    # Setup blacklist to ensure udev does not automatically mount Mender managed partitions
    install -d ${D}${sysconfdir}/udev/mount.blacklist.d
    echo ${MENDER_ROOTFS_PART_A} > ${D}${sysconfdir}/udev/mount.blacklist.d/mender
    echo ${MENDER_ROOTFS_PART_B} >> ${D}${sysconfdir}/udev/mount.blacklist.d/mender
}

do_install_append_mender-image_mender-systemd() {
    install -m 644 ${WORKDIR}/mender-client-data-dir.service ${D}${systemd_unitdir}/system/${MENDER_CLIENT}-data-dir.service
    install -d -m 755 ${D}${systemd_unitdir}/system/${MENDER_CLIENT}.service.wants
    ln -sf ../mender-client-data-dir.service ${D}${systemd_unitdir}/system/${MENDER_CLIENT}.service.wants/${MENDER_CLIENT}-data-dir.service
}

do_install_append_mender-growfs-data_mender-systemd() {
    sed -i "s#@MENDER_STORAGE_DEVICE@#${MENDER_STORAGE_DEVICE}#g" \
        ${WORKDIR}/mender-client-resize-data-part.sh.in

    sed -i "s#@MENDER_DATA_PART@#${MENDER_DATA_PART}#g" \
        ${WORKDIR}/mender-client-resize-data-part.sh.in

    sed -i "s#@MENDER_DATA_PART_NUMBER@#${MENDER_DATA_PART_NUMBER}#g" \
        ${WORKDIR}/mender-client-resize-data-part.sh.in

    install -m 0755 ${WORKDIR}/mender-client-resize-data-part.sh.in \
        ${D}/${bindir}/mender-client-resize-data-part

    install -d ${D}/${systemd_unitdir}/system
    install -m 644 ${WORKDIR}/mender-grow-data.service ${D}/${systemd_unitdir}/system/
    install -m 644 ${WORKDIR}/mender-systemd-growfs-data.service ${D}/${systemd_unitdir}/system/

    install -d ${D}${systemd_unitdir}/system/data.mount.wants/
    ln -sf ../mender-grow-data.service ${D}${systemd_unitdir}/system/data.mount.wants/
    ln -sf ../mender-systemd-growfs-data.service ${D}${systemd_unitdir}/system/data.mount.wants/
}

do_install_append_mender-persist-systemd-machine-id() {
    install -m 644 ${WORKDIR}/mender-client-systemd-machine-id.service ${D}${systemd_unitdir}/system/${MENDER_CLIENT}-systemd-machine-id.service
    install -d -m 755 ${D}${systemd_unitdir}/system/${MENDER_CLIENT}.service.wants
    ln -sf ../${MENDER_CLIENT}-systemd-machine-id.service ${D}${systemd_unitdir}/system/${MENDER_CLIENT}.service.wants/
    install -d -m 755 ${D}${bindir}
    install -m 755 ${WORKDIR}/mender-client-set-systemd-machine-id.sh ${D}${bindir}/${MENDER_CLIENT}-set-systemd-machine-id.sh
}
