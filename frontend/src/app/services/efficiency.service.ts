import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DepartmentEfficiency } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class EfficiencyService {
  private apiUrl = 'http://localhost:8080/api/efficiency';

  constructor(private http: HttpClient) { }

  getDailyRanking(date?: string): Observable<DepartmentEfficiency[]> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<DepartmentEfficiency[]>(`${this.apiUrl}/ranking`, { params });
  }

  getDepartmentTrend(departmentId: number, startDate: string, endDate: string): Observable<DepartmentEfficiency[]> {
    let params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate);
    return this.http.get<DepartmentEfficiency[]>(`${this.apiUrl}/department/${departmentId}/trend`, { params });
  }

  getAverageEfficiency(departmentId: number, startDate: string, endDate: string): Observable<number> {
    let params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate);
    return this.http.get<number>(`${this.apiUrl}/department/${departmentId}/average`, { params });
  }

  calculateTodayEfficiency(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/calculate`, {});
  }
}
