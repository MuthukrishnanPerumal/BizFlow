export type CatalogResource = 'categories' | 'brands' | 'units';

export interface CatalogConfig {
  resource: CatalogResource;
  title: string;
  singular: string;
  subtitle: string;
  createPermission: string;
  updatePermission: string;
  deletePermission: string;
  hasCode: boolean;
  hasShortName: boolean;
  hasDecimal: boolean;
  emptyTitle: string;
  emptyMessage: string;
}

export const CATALOG: Record<CatalogResource, CatalogConfig> = {
  categories: {
    resource: 'categories',
    title: 'Categories',
    singular: 'category',
    subtitle: 'Group products for purchasing, sales, and reports.',
    createPermission: 'PRODUCT_CREATE',
    updatePermission: 'PRODUCT_UPDATE',
    deletePermission: 'PRODUCT_DELETE',
    hasCode: true,
    hasShortName: false,
    hasDecimal: false,
    emptyTitle: 'No categories yet',
    emptyMessage: 'Add a category so products can be grouped for purchasing, sales, and reports.',
  },
  brands: {
    resource: 'brands',
    title: 'Brands',
    singular: 'brand',
    subtitle: 'Brands assigned to products.',
    createPermission: 'PRODUCT_CREATE',
    updatePermission: 'PRODUCT_UPDATE',
    deletePermission: 'PRODUCT_DELETE',
    hasCode: true,
    hasShortName: false,
    hasDecimal: false,
    emptyTitle: 'No brands yet',
    emptyMessage: 'Add a brand before you assign one to a product.',
  },
  units: {
    resource: 'units',
    title: 'Units',
    singular: 'unit',
    subtitle: 'Units of measure. Decimal units allow fractional quantities.',
    createPermission: 'PRODUCT_CREATE',
    updatePermission: 'PRODUCT_UPDATE',
    deletePermission: 'PRODUCT_DELETE',
    hasCode: false,
    hasShortName: true,
    hasDecimal: true,
    emptyTitle: 'No units yet',
    emptyMessage: 'Add a unit of measure before you create products.',
  },
};

export function catalogConfig(value: unknown): CatalogConfig {
  if (value === 'categories' || value === 'brands' || value === 'units') {
    return CATALOG[value];
  }
  return CATALOG.categories;
}
