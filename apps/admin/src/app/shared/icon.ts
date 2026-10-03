import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type IconName =
  'up' | 'down' | 'plus' | 'delete' | 'copy' | 'drag' | 'refresh' | 'check' | 'search' | 'close';

const PATHS: Record<IconName, string> = {
  up: 'M6 15l6-6 6 6',
  down: 'M6 9l6 6 6-6',
  plus: 'M12 5v14M5 12h14',
  delete: 'M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12',
  copy: 'M9 9h10v10H9zM5 15V5h10',
  drag: 'M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01',
  refresh: 'M20 12a8 8 0 1 1-2.5-5.8M20 4v5h-5',
  check: 'M5 12l5 5 9-10',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4',
  close: 'M6 6l12 12M18 6L6 18',
};

@Component({
  selector: 'admin-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="name() === 'drag' ? 3 : 2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      vertical-align: middle;
    }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  protected readonly path = computed(() => PATHS[this.name()]);
}
