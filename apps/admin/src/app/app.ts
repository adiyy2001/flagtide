import type { ElementRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, skip } from 'rxjs';
import { Workspace } from './state/workspace';

interface NavItem {
  readonly path: string;
  readonly label: string;
}

@Component({
  selector: 'admin-root',
  imports: [MatFormFieldModule, MatSelectModule, RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly workspace = inject(Workspace);
  private readonly router = inject(Router);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  protected readonly navigation: readonly NavItem[] = [
    { path: '/flags', label: 'Flags' },
    { path: '/segments', label: 'Segments' },
    { path: '/environments', label: 'Environments' },
    { path: '/audit', label: 'Audit log' },
    { path: '/propagation', label: 'Propagation' },
  ];

  constructor() {
    void this.workspace.load();
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        skip(1),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.main().nativeElement.focus({ preventScroll: true }));
  }

  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }

  protected selectEnvironment(key: string): void {
    this.workspace.select(key);
  }
}
