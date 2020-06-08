DESCRIPTION = "Mender image artifact library"
LICENSE = "Apache-2.0 & BSD-2-Clause & BSD-3-Clause & ISC & MIT"
LIC_FILES_CHKSUM = "file://${WORKDIR}/LIC_FILES_CHKSUM.sha256;md5=99143e34cf23a99976a299da9fa93bcf"

# Binary download, because go is outdate in this environment
SRC_URI = "\
    https://d1b0l86ne08fsf.cloudfront.net/mender-artifact/3.3.0/linux/mender-artifact;name=artifact \
    https://raw.githubusercontent.com/mendersoftware/mender-artifact/${SRCREV}/LIC_FILES_CHKSUM.sha256;name=license \
"

SRC_URI[artifact.md5sum] = "7b6735ccd7f6ab2fa4382ca4a4747fc1"
SRC_URI[artifact.sha256sum] = "40ddef65779179959aecba99dd5ddbfe936ba6b9f80f79a4dec1abd2a2c87989"

SRC_URI[license.md5sum] = "99143e34cf23a99976a299da9fa93bcf"
SRC_URI[license.sha256sum] = "be22e200d0826c94bfd71cc5c1d4995449a6e3bd527090c1cfe953bc81427573"

# Tag: 3.3.0
SRCREV = "b17a7a34da5be5c1e06fd139d94fca7d56b855cb"

DEPENDS += "xz"

BBCLASSEXTEND = "native"

do_install_class-native() {
    install -d ${D}${bindir}
    install ${WORKDIR}/mender-artifact -m 0755 ${D}${bindir}
}
