import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { CitizenComponent } from './components/citizen/citizen.component';
import { WorkbenchComponent } from './components/workbench/workbench.component';
import { MonitorComponent } from './components/monitor/monitor.component';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'citizen', component: CitizenComponent },
  { path: 'workbench', component: WorkbenchComponent },
  { path: 'monitor', component: MonitorComponent },
  { path: '**', redirectTo: '', pathMatch: 'full' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
