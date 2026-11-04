#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#

# MIRACLO version, shown in Settings > About phone.
#
# MIRACLO_VERSION        1.0
# MIRACLO_CODENAME       Aurora (the Android 17 line)
# MIRACLO_RELEASE_VARIANT empty for a release, or e.g. "beta" / "alpha_2";
#                        "_" becomes a space and the first letter is capitalised
#                        in Settings, so "beta_2" reads "Beta 2".

MIRACLO_VERSION_MAJOR := 1
MIRACLO_VERSION_MINOR := 0
MIRACLO_CODENAME := Aurora
MIRACLO_RELEASE_VARIANT ?=

MIRACLO_VERSION := $(MIRACLO_VERSION_MAJOR).$(MIRACLO_VERSION_MINOR)$(if $(MIRACLO_RELEASE_VARIANT),-$(MIRACLO_RELEASE_VARIANT))

# Build date in UTC, e.g. 20261101
MIRACLO_BUILD_DATE := $(shell date -u +%Y%m%d)

# Name of the zips and images, e.g. MIRACLO-1.0-Aurora-marble-20261101
MIRACLO_BUILD_NAME := MIRACLO-$(MIRACLO_VERSION)-$(MIRACLO_CODENAME)-$(MIRACLO_BUILD)-$(MIRACLO_BUILD_DATE)
