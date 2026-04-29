export interface Ticket {
  id: number;
  ticketNumber: string;
  title: string;
  content: string;
  category: string;
  subCategory: string;
  citizenName: string;
  citizenPhone: string;
  address: string;
  isAnonymous: boolean;
  status: string;
  alertLevel: string;
  processingHours: number;
  remainingHours: number;
  dueTime: string;
  isUrgent: boolean;
  priorityLevel: number;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
  closedAt: string;
  aiRecommendation: string;
  aiConfidence: number;
  satisfactionScore: number;
  satisfactionComment: string;
}

export interface TicketCreateDTO {
  title: string;
  content: string;
  category: string;
  subCategory: string;
  citizenName: string;
  citizenPhone: string;
  address: string;
  isAnonymous: boolean;
  isUrgent: boolean;
  priorityLevel: number;
  departmentId: number;
}

export interface TicketFlow {
  id: number;
  fromStatus: string;
  toStatus: string;
  operatorName: string;
  remark: string;
  operationContent: string;
  flowType: string;
  createdAt: string;
}

export interface SimilarTicket {
  ticketId: number;
  ticketNumber: string;
  title: string;
  similarity: number;
  status: string;
  departmentName: string;
}

export interface KnowledgeResult {
  knowledgeId: number;
  title: string;
  category: string;
  summary: string;
  relevanceScore: number;
}

export interface DepartmentEfficiency {
  id: number;
  department: {
    id: number;
    name: string;
  };
  statisticsDate: string;
  totalReceived: number;
  totalCompleted: number;
  totalOverdue: number;
  totalRedWarning: number;
  totalYellowWarning: number;
  averageProcessingHours: number;
  averageSatisfactionScore: number;
  onTimeCompletionRate: number;
  satisfactionRate: number;
  efficiencyScore: number;
  rank: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
