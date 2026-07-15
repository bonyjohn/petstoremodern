import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import { AllCommunityModule, CellValueChangedEvent, ColDef, ModuleRegistry } from 'ag-grid-community';

import { AdminService } from '../admin.service';
import { InventoryItem } from '../admin.models';

ModuleRegistry.registerModules([AllCommunityModule]);

/** Admin stock grid: on-hand quantities from the fulfillment service, edited in place. */
@Component({
  selector: 'app-admin-inventory',
  imports: [RouterLink, AgGridAngular],
  template: `
    <h2>Inventory <a routerLink="/admin/orders" class="switch">← Orders</a></h2>
    <ag-grid-angular
      class="inventory-grid"
      domLayout="autoHeight"
      [rowData]="inventory()"
      [columnDefs]="columnDefs"
      [defaultColDef]="defaultColDef"
      (cellValueChanged)="onCellValueChanged($event)"
    />
  `,
  styles: `
    h2 {
      font-size: 1.5rem;
      margin-bottom: 1.5rem;
    }
    .switch {
      font-size: 0.9rem;
      font-weight: 400;
      margin-left: 1rem;
    }
  `,
})
export class AdminInventoryPage {
  private readonly adminService = inject(AdminService);

  protected readonly inventory = signal<InventoryItem[]>([]);

  protected readonly defaultColDef: ColDef = { flex: 1, sortable: true, resizable: true };

  protected readonly columnDefs: ColDef<InventoryItem>[] = [
    { field: 'id', headerName: 'Item' },
    {
      field: 'quantityOnHand',
      headerName: 'Quantity on hand',
      editable: true,
      valueParser: (params) => Number(params.newValue),
    },
  ];

  constructor() {
    this.load();
  }

  onCellValueChanged(event: CellValueChangedEvent<InventoryItem>): void {
    const quantity = Number(event.newValue);
    if (!Number.isFinite(quantity) || quantity < 0) {
      this.load(); // reject the edit by reloading server state
      return;
    }
    this.adminService.updateInventory(event.data.id, quantity).subscribe(() => this.load());
  }

  private load(): void {
    this.adminService.inventory().subscribe((inventory) => {
      this.inventory.set(inventory);
    });
  }
}
