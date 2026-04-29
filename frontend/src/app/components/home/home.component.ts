import { Component, OnInit } from '@angular/core';
import { TicketService } from '../services/ticket.service';
import { EfficiencyService } from '../services/efficiency.service';
import { Ticket, DepartmentEfficiency } from '../models/models';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  recentTickets: Ticket[] = [];
  efficiencyRanking: DepartmentEfficiency[] = [];
  loading = true;
  error: string | null = null;

  stats = {
    total: 0,
    completed: 0,
    processing: 0,
    overdue: 0
  };

  constructor(
    private ticketService: TicketService,
    private efficiencyService: EfficiencyService
  ) { }

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.error = null;

    this.ticketService.getAllTickets(0, 5).subscribe({
      next: (response) => {
        this.recentTickets = response.content;
        this.stats.total = response.totalElements;
        
        this.recentTickets.forEach(ticket => {
          if (ticket.status === 'COMPLETED' || ticket.status === 'CLOSED') {
            this.stats.completed++;
          } else if (ticket.alertLevel === 'OVERDUE') {
            this.stats.overdue++;
          } else {
            this.stats.processing++;
          }
        });
      },
      error: (err) => {
        this.error = '加载工单数据失败';
        console.error('Error loading tickets:', err);
      }
    });

    this.efficiencyService.getDailyRanking().subscribe({
      next: (data) => {
        this.efficiencyRanking = data.slice(0, 5);
      },
      error: (err) => {
        console.error('Error loading efficiency data:', err);
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  getStatusBadgeClass(status: string): string {
    const statusMap: { [key: string]: string } = {
      'SUBMITTED': 'badge-info',
      'ASSIGNED': 'badge-info',
      'ACCEPTED': 'badge-info',
      'IN_PROGRESS': 'badge-secondary',
      'TRANSFERRED': 'badge-warning',
      'COOPERATING': 'badge-warning',
      'RETURNED': 'badge-danger',
      'PENDING_REVIEW': 'badge-warning',
      'COMPLETED': 'badge-success',
      'VISITING': 'badge-info',
      'CLOSED': 'badge-success',
      'CANCELLED': 'badge-secondary'
    };
    return statusMap[status] || 'badge-secondary';
  }

  getStatusText(status: string): string {
    const statusMap: { [key: string]: string } = {
      'SUBMITTED': '已提交',
      'ASSIGNED': '已派单',
      'ACCEPTED': '已接单',
      'IN_PROGRESS': '办理中',
      'TRANSFERRED': '已转办',
      'COOPERATING': '协办中',
      'RETURNED': '已退回',
      'PENDING_REVIEW': '待审核',
      'COMPLETED': '已办结',
      'VISITING': '回访中',
      'CLOSED': '已关闭',
      'CANCELLED': '已取消'
    };
    return statusMap[status] || status;
  }

  getAlertClass(alertLevel: string): string {
    const alertMap: { [key: string]: string } = {
      'NORMAL': 'alert-normal',
      'YELLOW_WARNING': 'alert-yellow',
      'RED_WARNING': 'alert-red',
      'OVERDUE': 'alert-overdue'
    };
    return alertMap[alertLevel] || 'alert-normal';
  }

  getAlertText(alertLevel: string): string {
    const alertMap: { [key: string]: string } = {
      'NORMAL': '正常',
      'YELLOW_WARNING': '黄牌警告',
      'RED_WARNING': '红牌警告',
      'OVERDUE': '已逾期'
    };
    return alertMap[alertLevel] || '正常';
  }
}
