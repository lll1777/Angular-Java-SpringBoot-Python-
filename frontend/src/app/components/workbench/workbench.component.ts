import { Component, OnInit } from '@angular/core';
import { TicketService } from '../../services/ticket.service';
import { Ticket, TicketFlow, SimilarTicket, KnowledgeResult } from '../../models/models';
import { FormBuilder, FormGroup } from '@angular/forms';

@Component({
  selector: 'app-workbench',
  templateUrl: './workbench.component.html',
  styleUrls: ['./workbench.component.css']
})
export class WorkbenchComponent implements OnInit {
  tickets: Ticket[] = [];
  selectedTicket: Ticket | null = null;
  ticketFlows: TicketFlow[] = [];
  similarTickets: SimilarTicket[] = [];
  recommendedKnowledge: KnowledgeResult[] = [];
  
  loading = false;
  error: string | null = null;
  
  actionForm: FormGroup;
  showingActionPanel = false;
  currentAction: string = '';
  
  actionTypes = [
    { value: 'accept', label: '接单', icon: '✓' },
    { value: 'process', label: '开始办理', icon: '▶' },
    { value: 'transfer', label: '转办', icon: '↪' },
    { value: 'complete', label: '办结', icon: '✅' },
    { value: 'close', label: '关闭', icon: '✕' }
  ];

  statusFilter = 'ALL';
  statusOptions = [
    { value: 'ALL', label: '全部状态' },
    { value: 'ASSIGNED', label: '待接单' },
    { value: 'ACCEPTED', label: '已接单' },
    { value: 'IN_PROGRESS', label: '办理中' },
    { value: 'COMPLETED', label: '已办结' },
    { value: 'PENDING_REVIEW', label: '待审核' }
  ];

  departments = [
    { id: 1, name: '城市管理局' },
    { id: 2, name: '交通运输局' },
    { id: 3, name: '教育局' },
    { id: 4, name: '卫生健康委员会' },
    { id: 5, name: '人力资源和社会保障局' },
    { id: 6, name: '住房和城乡建设局' },
    { id: 7, name: '市场监督管理局' },
    { id: 8, name: '生态环境局' },
    { id: 9, name: '公安局' },
    { id: 10, name: '行政审批服务局' },
    { id: 11, name: '政府服务热线中心' }
  ];

  constructor(
    private ticketService: TicketService,
    private fb: FormBuilder
  ) {
    this.actionForm = this.fb.group({
      content: [''],
      targetDepartmentId: [null],
      reason: [''],
      satisfaction: [null],
      comment: ['']
    });
  }

  ngOnInit(): void {
    this.loadTickets();
  }

  loadTickets(): void {
    this.loading = true;
    this.error = null;
    
    this.ticketService.getAllTickets(0, 20).subscribe({
      next: (response) => {
        this.tickets = response.content;
        this.loading = false;
      },
      error: (err) => {
        this.error = '加载工单数据失败';
        this.loading = false;
        console.error('Error loading tickets:', err);
      }
    });
  }

  selectTicket(ticket: Ticket): void {
    this.selectedTicket = ticket;
    this.loading = true;
    
    this.ticketService.getTicketFlows(ticket.id).subscribe({
      next: (flows) => {
        this.ticketFlows = flows;
      },
      error: (err) => {
        console.error('Error loading ticket flows:', err);
      }
    });
    
    this.ticketService.getSimilarTickets(ticket.id).subscribe({
      next: (similar) => {
        this.similarTickets = similar;
      },
      error: (err) => {
        console.error('Error loading similar tickets:', err);
      }
    });
    
    this.ticketService.getRecommendedKnowledge(ticket.id).subscribe({
      next: (knowledge) => {
        this.recommendedKnowledge = knowledge;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error loading knowledge:', err);
        this.loading = false;
      }
    });
  }

  openActionPanel(action: string): void {
    this.currentAction = action;
    this.showingActionPanel = true;
    this.actionForm.reset();
  }

  closeActionPanel(): void {
    this.showingActionPanel = false;
    this.currentAction = '';
  }

  executeAction(): void {
    if (!this.selectedTicket) return;
    
    const formValue = this.actionForm.value;
    
    switch (this.currentAction) {
      case 'accept':
        this.ticketService.acceptTicket(this.selectedTicket.id).subscribe({
          next: (ticket) => {
            this.selectedTicket = ticket;
            this.closeActionPanel();
            this.loadTickets();
          },
          error: (err) => {
            this.error = '接单失败：' + err.message;
          }
        });
        break;
        
      case 'process':
        this.ticketService.startProcessing(this.selectedTicket.id, formValue.content).subscribe({
          next: (ticket) => {
            this.selectedTicket = ticket;
            this.closeActionPanel();
            this.loadTickets();
          },
          error: (err) => {
            this.error = '开始办理失败：' + err.message;
          }
        });
        break;
        
      case 'transfer':
        if (!formValue.targetDepartmentId) {
          this.error = '请选择目标部门';
          return;
        }
        this.ticketService.transferTicket(
          this.selectedTicket.id,
          formValue.targetDepartmentId,
          formValue.reason || ''
        ).subscribe({
          next: (ticket) => {
            this.selectedTicket = ticket;
            this.closeActionPanel();
            this.loadTickets();
          },
          error: (err) => {
            this.error = '转办失败：' + err.message;
          }
        });
        break;
        
      case 'complete':
        this.ticketService.completeTicket(this.selectedTicket.id, formValue.content).subscribe({
          next: (ticket) => {
            this.selectedTicket = ticket;
            this.closeActionPanel();
            this.loadTickets();
          },
          error: (err) => {
            this.error = '办结失败：' + err.message;
          }
        });
        break;
        
      case 'close':
        this.ticketService.closeTicket(
          this.selectedTicket.id,
          formValue.satisfaction,
          formValue.comment
        ).subscribe({
          next: (ticket) => {
            this.selectedTicket = ticket;
            this.closeActionPanel();
            this.loadTickets();
          },
          error: (err) => {
            this.error = '关闭失败：' + err.message;
          }
        });
        break;
    }
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

  canPerformAction(action: string): boolean {
    if (!this.selectedTicket) return false;
    const status = this.selectedTicket.status;
    
    switch (action) {
      case 'accept':
        return status === 'ASSIGNED' || status === 'TRANSFERRED';
      case 'process':
        return status === 'ACCEPTED';
      case 'transfer':
        return status === 'ASSIGNED' || status === 'ACCEPTED' || status === 'IN_PROGRESS';
      case 'complete':
        return status === 'IN_PROGRESS' || status === 'PENDING_REVIEW';
      case 'close':
        return status === 'COMPLETED' || status === 'VISITING';
      default:
        return false;
    }
  }

  getAvailableActions(): string[] {
    const actions: string[] = [];
    if (this.canPerformAction('accept')) actions.push('accept');
    if (this.canPerformAction('process')) actions.push('process');
    if (this.canPerformAction('transfer')) actions.push('transfer');
    if (this.canPerformAction('complete')) actions.push('complete');
    if (this.canPerformAction('close')) actions.push('close');
    return actions;
  }

  getActionLabel(action: string): string {
    const labels: { [key: string]: string } = {
      'accept': '接单',
      'process': '开始办理',
      'transfer': '转办',
      'complete': '办结',
      'close': '关闭'
    };
    return labels[action] || action;
  }
}
