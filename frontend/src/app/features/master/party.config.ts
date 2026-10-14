export type PartyResource = 'suppliers' | 'customers';

export interface PartyConfig {
  resource: PartyResource;
  title: string;
  singular: string;
  subtitle: string;
  viewPermission: string;
  createPermission: string;
  updatePermission: string;
  deletePermission: string;
  codeField: 'supplierCode' | 'customerCode';
  nameField: 'supplierName' | 'customerName';
  nameLabel: string;
  balanceLabel: string;
  historyHint: string;
  hasContact: boolean;
  emptyTitle: string;
  emptyMessage: string;
  documentLabel: string;
  documentRoute: string;
  createDocumentPermission: string;
  newDocumentRoute: string;
}

export const PARTIES: Record<PartyResource, PartyConfig> = {
  suppliers: {
    resource: 'suppliers',
    title: 'Suppliers',
    singular: 'supplier',
    subtitle: 'Parties that supply goods. Opening balances post to the supplier account.',
    viewPermission: 'SUPPLIER_VIEW',
    createPermission: 'SUPPLIER_CREATE',
    updatePermission: 'SUPPLIER_UPDATE',
    deletePermission: 'SUPPLIER_DELETE',
    codeField: 'supplierCode',
    nameField: 'supplierName',
    nameLabel: 'Supplier name',
    balanceLabel: 'Payable balance',
    historyHint: 'Purchases, returns, and payments will appear in this history.',
    hasContact: true,
    emptyTitle: 'No suppliers yet',
    emptyMessage: 'Add a supplier before you record a purchase.',
    documentLabel: 'Purchases',
    documentRoute: '/purchases',
    createDocumentPermission: 'PURCHASE_CREATE',
    newDocumentRoute: '/purchases/new',
  },
  customers: {
    resource: 'customers',
    title: 'Customers',
    singular: 'customer',
    subtitle: 'Parties that buy goods. Opening balances post to the customer account.',
    viewPermission: 'CUSTOMER_VIEW',
    createPermission: 'CUSTOMER_CREATE',
    updatePermission: 'CUSTOMER_UPDATE',
    deletePermission: 'CUSTOMER_DELETE',
    codeField: 'customerCode',
    nameField: 'customerName',
    nameLabel: 'Customer name',
    balanceLabel: 'Receivable balance',
    historyHint: 'Sales, returns, and payments will appear in this history.',
    hasContact: false,
    emptyTitle: 'No customers yet',
    emptyMessage: 'Add a customer before you record a sale.',
    documentLabel: 'Sales',
    documentRoute: '/sales',
    createDocumentPermission: 'SALES_CREATE',
    newDocumentRoute: '/sales/new',
  },
};

export function partyConfig(value: unknown): PartyConfig {
  if (value === 'suppliers' || value === 'customers') {
    return PARTIES[value];
  }
  return PARTIES.suppliers;
}
