export interface SystemInfo {
  name: string;
  version: string;
  environment: string;
  apiVersion: string;
  serverTime: string;
}

export interface SystemHealth {
  status: string;
  database: string;
  appliedMigrations: number;
}

export interface CommercialDefaults {
  displayName: string;
  currencyCode: string;
  locale: string;
  dateFormat?: string;
  decimalPrecision?: number;
  roundingRule?: string;
  defaultPaymentMethod?: string;
}

export interface PlatformCheck {
  id: string;
  name: string;
  detail: string;
  status: string;
}
