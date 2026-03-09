package viaduct.tenant.runtime.featuretests.fixtures

import viaduct.tenant.runtime.bootstrap.TenantPackageFinder

class TestTenantPackageFinder(
    private val packageToResolverBases: Map<String, Set<Class<*>>>
) : TenantPackageFinder {
    override fun tenantPackages() = packageToResolverBases.keys
}
