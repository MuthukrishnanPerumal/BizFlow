export interface NavNode {
  label: string;
  icon: string;
  route?: string;
  permission?: string;
  children?: NavNode[];
}

export interface Crumb {
  label: string;
  url: string;
  link?: boolean;
}
