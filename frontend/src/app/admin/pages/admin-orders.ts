import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SelectModule } from 'primeng/select';
import { AgGridAngular } from 'ag-grid-angular';
import { AllCommunityModule, CellClickedEvent, ColDef, ModuleRegistry } from 'ag-grid-community';

import { AdminService } from '../admin.service';
import { AdminOrderResponse } from '../admin.models';

ModuleRegistry.registerModules([AllCommunityModule]);

const STATUSES = ['PENDING', 'APPROVED', 'DENIED', 'PARTIALLY_SHIPPED', 'COMPLETED', 'ALL'] as const;

/** Admin order queue: the modern face of the legacy admin webapp's approve/deny grid. */
@Component({
  selector: 'app-admin-orders',
  imports: [FormsModule, RouterLink, SelectModule, AgGridAngular],
  template: `
    <div class="header">
      <h2>Orders <a routerLink="/admin/inventory" class="switch">Inventory →</a></h2>
      <p-select
        [options]="statuses"
        [ngModel]="statusFilter()"
        (ngModelChange)="onStatusChange($event)"
      />
    </div>
    <ag-grid-angular
      class="orders-grid"
      domLayout="autoHeight"
      [rowData]="orders()"
      [columnDefs]="columnDefs"
      [defaultColDef]="defaultColDef"
      (cellClicked)="onCellClicked($event)"
    />
  `,
  styles: `
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    h2 {
      font-size: 1.5rem;
      margin: 0;
    }
    .switch {
      font-size: 0.9rem;
      font-weight: 400;
      margin-left: 1rem;
    }
  `,
})
export class AdminOrdersPage {
  private readonly adminService = inject(AdminService);

  protected readonly statuses = [...STATUSES];
  protected readonly statusFilter = signal<string>('PENDING');
  protected readonly orders = signal<AdminOrderResponse[]>([]);

  protected readonly defaultColDef: ColDef = { flex: 1, sortable: true, resizable: true };

  protected readonly columnDefs: ColDef<AdminOrderResponse>[] = [
    { field: 'orderId', headerName: 'Order' },
    { field: 'userId', headerName: 'User' },
    {
      field: 'orderDate',
      headerName: 'Date',
      valueFormatter: (params) => new Date(params.value).toLocaleString(),
      flex: 2,
    },
    { field: 'locale', headerName: 'Locale' },
    { field: 'totalValue', headerName: 'Total' },
    { field: 'status', headerName: 'Status' },
    {
      colId: 'actions',
      headerName: '',
      sortable: false,
      cellRenderer: (params: { data?: AdminOrderResponse }) =>
        params.data?.status === 'PENDING'
          ? `<button data-action="approve">Approve</button> <button data-action="deny">Deny</button>`
          : '',
      flex: 2,
    },
  ];

  constructor() {
    this.load();
  }

  onStatusChange(status: string): void {
    this.statusFilter.set(status);
    this.load();
  }

  onCellClicked(event: CellClickedEvent<AdminOrderResponse>): void {
    if (event.colDef.colId !== 'actions' || !event.data) {
      return;
    }
    const action = (event.event?.target as HTMLElement | null)?.dataset?.['action'];
    if (action === 'approve') {
      this.adminService.approve(event.data.orderId).subscribe(() => this.load());
    } else if (action === 'deny') {
      this.adminService.deny(event.data.orderId).subscribe(() => this.load());
    }
  }

  private load(): void {
    const status = this.statusFilter();
    this.adminService.orders(status === 'ALL' ? undefined : status).subscribe((orders) => {
      this.orders.set(orders);
    });
  }
}
