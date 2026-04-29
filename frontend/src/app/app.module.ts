import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './components/home/home.component';
import { CitizenComponent } from './components/citizen/citizen.component';
import { WorkbenchComponent } from './components/workbench/workbench.component';
import { MonitorComponent } from './components/monitor/monitor.component';

import { TicketService } from './services/ticket.service';
import { EfficiencyService } from './services/efficiency.service';

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    CitizenComponent,
    WorkbenchComponent,
    MonitorComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule,
    ReactiveFormsModule,
    HttpClientModule
  ],
  providers: [
    TicketService,
    EfficiencyService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
