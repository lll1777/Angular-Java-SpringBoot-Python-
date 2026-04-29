import { Component, OnInit } from '@angular/core';
import { EfficiencyService } from '../../services/efficiency.service';
import { TicketService } from '../../services/ticket.service';
import { DepartmentEfficiency, Ticket } from '../../models/models';

@Component({
  selector: 'app-monitor',
  templateUrl: './monitor.component.html',
  styleUrls: ['./monitor.component.css']
})
export class MonitorComponent implements OnInit {
  efficiencyRanking: DepartmentEfficiency[] = [];
  highAlertTickets: Ticket[] = [];
  loading = true;
  error: string | null = null;
  
  overallStats = {
    totalDepartments: 0,
    avgEfficiency: 0,
    avgOnTimeRate: 0,
    avgSatisfaction: 0
  };

  constructor(
    private efficiencyService: EfficiencyService,
    private ticketService: TicketService
  ) { }

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.error = null;

    this.efficiencyService.getDailyRanking().subscribe({
      next: (data) => {
        this.efficiencyRanking = data;
        this.calculateOverallStats();
        this.loading = false;
      },
      error: (err) => {
        this.error = '加载效能数据失败';
        this.loading = false;
        console.error('Error loading efficiency data:', err);
      }
    });
  }

  calculateOverallStats(): void {
    if (this.efficiencyRanking.length === 0) return;

    this.overallStats.totalDepartments = this.efficiencyRanking.length;
    
    const validEfficiencies = this.efficiencyRanking.filter(d => d.efficiencyScore !== null && d.efficiencyScore !== undefined);
    this.overallStats.avgEfficiency = validEfficiencies.length > 0 
      ? validEfficiencies.reduce((sum, d) => sum + (d.efficiencyScore || 0), 0) / validEfficiencies.length 
      : 0;

    const validOnTimeRates = this.efficiencyRanking.filter(d => d.onTimeCompletionRate !== null && d.onTimeCompletionRate !== undefined);
    this.overallStats.avgOnTimeRate = validOnTimeRates.length > 0 
      ? validOnTimeRates.reduce((sum, d) => sum + (d.onTimeCompletionRate || 0), 0) / validOnTimeRates.length 
      : 0;

    const validSatisfactions = this.efficiencyRanking.filter(d => d.satisfactionRate !== null && d.satisfactionRate !== undefined);
    this.overallStats.avgSatisfaction = validSatisfactions.length > 0 
      ? validSatisfactions.reduce((sum, d) => sum + (d.satisfactionRate || 0), 0) / validSatisfactions.length 
      : 0;
  }

  getRankBadgeClass(rank: number): string {
    if (rank === 1) return 'badge-danger';
    if (rank === 2) return 'badge-warning';
    if (rank === 3) return 'badge-info';
    return 'badge-secondary';
  }

  getEfficiencyLevel(score: number | null | undefined): string {
    if (score === null || score === undefined) return 'normal';
    if (score >= 80) return 'excellent';
    if (score >= 60) return 'good';
    if (score >= 40) return 'average';
    return 'poor';
  }

  getEfficiencyLevelClass(score: number | null | undefined): string {
    const level = this.getEfficiencyLevel(score);
    const classMap: { [key: string]: string } = {
      'excellent': 'badge-success',
      'good': 'badge-info',
      'average': 'badge-warning',
      'poor': 'badge-danger',
      'normal': 'badge-secondary'
    };
    return classMap[level] || 'badge-secondary';
  }

  getEfficiencyLevelText(score: number | null | undefined): string {
    const level = this.getEfficiencyLevel(score);
    const textMap: { [key: string]: string } = {
      'excellent': '优秀',
      'good': '良好',
      'average': '一般',
      'poor': '较差',
      'normal': '未评级'
    };
    return textMap[level] || '未评级';
  }

  calculateToday(): void {
    this.efficiencyService.calculateTodayEfficiency().subscribe({
      next: () => {
        this.loadData();
      },
      error: (err) => {
        this.error = '计算失败';
        console.error('Error calculating efficiency:', err);
      }
    });
  }

  exportData(): void {
    if (this.efficiencyRanking.length === 0) {
      this.error = '暂无数据可导出';
      return;
    }

    const headers = ['排名', '部门名称', '效能分数', '评价等级', '办结率(%)', '满意度(%)', '接收工单', '已办结', '逾期工单', '红牌警告', '黄牌警告'];
    const rows = this.efficiencyRanking.map(item => [
      item.rank,
      item.department?.name || '',
      item.efficiencyScore?.toFixed(2) || '0',
      this.getEfficiencyLevelText(item.efficiencyScore),
      item.onTimeCompletionRate?.toFixed(2) || '0',
      item.satisfactionRate?.toFixed(2) || '0',
      item.totalReceived,
      item.totalCompleted,
      item.totalOverdue,
      item.totalRedWarning,
      item.totalYellowWarning
    ]);

    const csvContent = [headers.join(','), ...rows.map(row => row.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `效能报表_${new Date().toISOString().split('T')[0]}.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
}
