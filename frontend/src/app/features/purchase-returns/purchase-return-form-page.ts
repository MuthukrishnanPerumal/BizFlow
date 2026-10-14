import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { NotificationService } from '../../core/services/notification.service';
import { explainError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { amount, moneyText } from '../purchases/purchase-money';
import { PurchaseApi } from '../purchases/purchase-api.service';
import { PurchaseDetail, PurchaseLine } from '../purchases/purchase.model';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';

interface ReturnRow {
  item: PurchaseLine;
  quantity: FormControl<string>;
  freeQuantity: FormControl<string>;
  serials: FormControl<string>;
}

@Component({
  selector: 'app-purchase-return-form-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink, PageHeader, SkeletonLoader],
  templateUrl: './purchase-return-form-page.html',
  styleUrls: ['../purchases/purchase-screen.scss', '../sales/transaction-workspace.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseReturnFormPage implements UnsavedForm {
  private readonly api = inject(PurchaseApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly returnDate = new FormControl(today(), { nonNullable: true });
  readonly notes = new FormControl('', { nonNullable: true });
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly errorMessage = signal('');
  readonly purchase = signal<PurchaseDetail | null>(null);
  readonly rows = signal<ReturnRow[]>([]);

  constructor() {
    const purchaseId = Number(this.route.snapshot.queryParamMap.get('purchaseId'));
    if (!purchaseId) {
      this.loading.set(false);
      this.errorMessage.set('Open a purchase and choose Return goods.');
      return;
    }
    this.api.get(purchaseId).subscribe({
      next: (purchase) => {
        this.purchase.set(purchase);
        this.rows.set(
          purchase.items
            .filter((item) => amount(item.remainingQuantity) > 0 || amount(item.remainingFreeQuantity) > 0)
            .map((item) => ({
              item,
              quantity: new FormControl('0', { nonNullable: true }),
              freeQuantity: new FormControl('0', { nonNullable: true }),
              serials: new FormControl('', { nonNullable: true }),
            })),
        );
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(explainError(error));
      },
    });
  }

  hasUnsavedChanges(): boolean {
    return !this.saved() && this.rows().some((row) => row.quantity.dirty || row.freeQuantity.dirty || this.notes.dirty);
  }

  rowError(row: ReturnRow): string | null {
    const quantity = amount(row.quantity.value);
    const free = amount(row.freeQuantity.value);
    if (quantity < 0 || free < 0) {
      return 'Quantity cannot be negative';
    }
    if (quantity > amount(row.item.remainingQuantity)) {
      return `Return quantity is above the quantity still on this purchase. Available: ${row.item.remainingQuantity}`;
    }
    if (free > amount(row.item.remainingFreeQuantity)) {
      return `Free quantity is above the free quantity still available. Available: ${row.item.remainingFreeQuantity}`;
    }
    if (row.item.serialTracking && quantity + free > 0) {
      const serials = row.serials.value.split(/[\s,]+/).filter((value) => value.trim().length > 0);
      if (serials.length !== quantity + free) {
        return 'Select each serial number being returned';
      }
    }
    return null;
  }

  selectedCount(): number {
    return this.rows().filter((row) => amount(row.quantity.value) > 0 || amount(row.freeQuantity.value) > 0).length;
  }

  canSave(): boolean {
    const purchase = this.purchase();
    if (!purchase || this.saving() || !purchase.returnable || !this.returnDate.value) {
      return false;
    }
    const selected = this.rows().filter((row) => amount(row.quantity.value) > 0 || amount(row.freeQuantity.value) > 0);
    return selected.length > 0 && selected.every((row) => this.rowError(row) === null);
  }

  save(): void {
    const purchase = this.purchase();
    if (!purchase || !this.canSave()) {
      this.errorMessage.set('Enter a return quantity that is still available on the purchase.');
      return;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .createReturn({
        purchaseId: purchase.id,
        purchaseVersion: purchase.version,
        returnDate: this.returnDate.value,
        notes: this.notes.value.trim() || null,
        items: this.rows()
          .filter((row) => amount(row.quantity.value) > 0 || amount(row.freeQuantity.value) > 0)
          .map((row) => ({
            purchaseItemId: row.item.id,
            quantity: moneyText(amount(row.quantity.value)),
            freeQuantity: moneyText(amount(row.freeQuantity.value)),
            serials: row.serials.value.split(/[\s,]+/).map((value) => value.trim()).filter((value) => value.length > 0),
          })),
      })
      .subscribe({
        next: (created) => {
          this.saved.set(true);
          this.notifications.success('Purchase return posted');
          void this.router.navigate(['/purchase-returns', created.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.errorMessage.set(explainError(error));
        },
      });
  }
}

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
