import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Ticket, TicketCreateDTO, TicketFlow, SimilarTicket, KnowledgeResult, PageResponse } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class TicketService {
  private apiUrl = 'http://localhost:8080/api/tickets';

  constructor(private http: HttpClient) { }

  createTicket(ticket: TicketCreateDTO): Observable<Ticket> {
    return this.http.post<Ticket>(this.apiUrl, ticket);
  }

  getTicketById(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.apiUrl}/${id}`);
  }

  getTicketByNumber(ticketNumber: string): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.apiUrl}/number/${ticketNumber}`);
  }

  getAllTickets(page: number = 0, size: number = 10): Observable<PageResponse<Ticket>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<Ticket>>(this.apiUrl, { params });
  }

  getTicketsByDepartment(departmentId: number, page: number = 0, size: number = 10): Observable<PageResponse<Ticket>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<Ticket>>(`${this.apiUrl}/department/${departmentId}`, { params });
  }

  getTicketsByStatus(status: string, page: number = 0, size: number = 10): Observable<PageResponse<Ticket>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<Ticket>>(`${this.apiUrl}/status/${status}`, { params });
  }

  assignTicket(id: number, departmentId?: number): Observable<Ticket> {
    let params = new HttpParams();
    if (departmentId) {
      params = params.set('departmentId', departmentId.toString());
    }
    return this.http.post<Ticket>(`${this.apiUrl}/${id}/assign`, {}, { params });
  }

  acceptTicket(id: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.apiUrl}/${id}/accept`, {});
  }

  startProcessing(id: number, content?: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.apiUrl}/${id}/process`, { content });
  }

  transferTicket(id: number, targetDepartmentId: number, reason: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.apiUrl}/${id}/transfer`, {
      targetDepartmentId,
      reason
    });
  }

  completeTicket(id: number, content?: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.apiUrl}/${id}/complete`, { content });
  }

  closeTicket(id: number, satisfaction?: number, comment?: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.apiUrl}/${id}/close`, {
      satisfaction,
      comment
    });
  }

  getTicketFlows(id: number): Observable<TicketFlow[]> {
    return this.http.get<TicketFlow[]>(`${this.apiUrl}/${id}/flows`);
  }

  getSimilarTickets(id: number): Observable<SimilarTicket[]> {
    return this.http.get<SimilarTicket[]>(`${this.apiUrl}/${id}/similar`);
  }

  getRecommendedKnowledge(id: number): Observable<KnowledgeResult[]> {
    return this.http.get<KnowledgeResult[]>(`${this.apiUrl}/${id}/knowledge`);
  }
}
