DESCRIPTION = "Mender tool for doing OTA software updates."
HOMEPAGE = "https://mender.io"
LIC_FILES_CHKSUM = "file://LIC_FILES_CHKSUM.sha256;md5=80ba3790b689991e47685da401fd3375"
LICENSE = "Apache-2.0 & BSD-2-Clause & BSD-3-Clause & ISC & MIT & OLDAP-2.8"

SRC_URI = "\
    git://github.com/mendersoftware/mender;protocol=https;branch=2.2.x \
    https://d1b0l86ne08fsf.cloudfront.net/2.2.0/dist-packages/debian/armhf/mender-client_2.2.0-1_armhf.deb;name=mender-bin;unpack=0 \
"

SRC_URI[mender-bin.md5sum] = "ec294ecb2ec4eb0503cab82fb6696f44"
SRC_URI[mender-bin.sha256sum] = "1599cf9b4d53a89ae11c153de0358a3e26f5a789688e5b340c80a88f5a1357aa"

# Tag: 2.2.0
SRCREV = "44753ca67caba0deea203a7b9d7785c71a0c05b4"

DEPENDS += "xz"
RDEPENDS_${PN} += "liblzma"

# MEN-2948: systemd service is still named mender.service in 2.2.x
MENDER_CLIENT = "mender"

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
MENDER_UPDATE_POLL_INTERVAL_SECONDS ?= "1800"
MENDER_INVENTORY_POLL_INTERVAL_SECONDS ?= "28800"
MENDER_RETRY_POLL_INTERVAL_SECONDS ?= "300"

S = "${WORKDIR}/git"

inherit pkgconfig

FILES_${PN} += "\
    ${bindir}/mender \
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
    /data/mender \
    /data/mender/device_type \
    /data/mender/mender.conf \
"

SYSROOT_DIRS += "/data"

PACKAGECONFIG ??= "modules"
PACKAGECONFIG_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-install', ' mender-install', '', d)}"
PACKAGECONFIG_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-uboot', ' u-boot', '', d)}"
PACKAGECONFIG_append = "${@bb.utils.contains('DISTRO_FEATURES', 'mender-grub', ' grub', '', d)}"

PACKAGECONFIG[mender-install] = ",,,mender-artifact-info ca-certificates"
PACKAGECONFIG[u-boot] = ",,,u-boot-fw-utils"
PACKAGECONFIG[grub] = ",,,grub-editenv grub-mender-grubenv"
# The docker module depends on bash, and of course on docker. However, docker is
# a very large requirement, which we will not mandate. Bash however, we require,
# because otherwise the Yocto QA checks will complain.
PACKAGECONFIG[modules] = ",,,bash"

do_configure() {
    :
}

do_compile() {
    cd ${WORKDIR}
    ar x ${WORKDIR}/mender-client_2.2.0-1_armhf.deb

    tar xvf data.tar.xz -C ${B}

    echo "device_type=${MENDER_DEVICE_TYPE}" > ${B}/device_type
}

python do_prepare_mender_conf() {
    import json

    # If a mender.conf has been provided in SRC_URI, merge this with the
    # settings we generate. The settings specified by variables take precedence.
    src_conf = os.path.join(d.getVar("WORKDIR", True), "mender.conf")
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
            value = d.getVar(value, True)
        else:
            warn_str = "automatically provided settings"
        if value is not None and value != "":
            if transient_conf.get(key) is not None and transient_conf[key] != value:
                bb.warn("Configuration key '%s', found in mender.conf, conflicts with %s. Choosing the latter." % (key, warn_str))
            if integer:
                transient_conf[key] = int(value)
            else:
                transient_conf[key] = value

    key_in_src_uri = os.path.exists(os.path.join(d.getVar("WORKDIR", True), "artifact-verify-key.pem"))
    key_in_var = d.getVar("MENDER_ARTIFACT_VERIFY_KEY", True) not in [None, ""]

    # Add new variable -> config assignments here.
    if key_in_src_uri or key_in_var:
        conf_maybe_add("ArtifactVerifyKey", "%s/mender/artifact-verify-key.pem" % d.getVar("sysconfdir", True), getvar=False, integer=False)
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

    dst_conf = os.path.join(d.getVar("B", True), "transient_mender.conf")
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
        V=1 \
        prefix=${D} \
        bindir=${bindir} \
        datadir=${datadir} \
        sysconfdir=${sysconfdir} \
        install-identity-scripts \
        install-inventory-scripts \
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

    install -d ${D}/${bindir}
    install -m 755 ${B}/${bindir}/mender ${D}/${bindir}/mender
}

COMPATIBLE_MACHINE = "m-com"

INSANE_SKIP_${PN} += "already-stripped ldflags"
