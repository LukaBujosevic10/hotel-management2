import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

/**
 * Application header (top bar). Used by the shell and available to any page
 * that renders outside the shell.
 */
@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, MatToolbarModule, MatIconModule, MatButtonModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent {
  @Input() brand = 'Hotel Lepenica';
  @Input() showLogout = true;
  @Input() username = '';
  @Input() role = '';

  @Output() logout = new EventEmitter<void>();
}
