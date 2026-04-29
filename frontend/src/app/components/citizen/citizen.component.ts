import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TicketService } from '../../services/ticket.service';
import { Ticket, TicketCreateDTO } from '../../models/models';
import { Router } from '@angular/router';

@Component({
  selector: 'app-citizen',
  templateUrl: './citizen.component.html',
  styleUrls: ['./citizen.component.css']
})
export class CitizenComponent {
  ticketForm: FormGroup;
  submitting = false;
  success = false;
  error: string | null = null;
  createdTicket: Ticket | null = null;

  categories = [
    { value: '城市管理', label: '城市管理' },
    { value: '环境卫生', label: '环境卫生' },
    { value: '交通出行', label: '交通出行' },
    { value: '教育资源', label: '教育资源' },
    { value: '医疗卫生', label: '医疗卫生' },
    { value: '社会保障', label: '社会保障' },
    { value: '住房保障', label: '住房保障' },
    { value: '市场监管', label: '市场监管' },
    { value: '环境保护', label: '环境保护' },
    { value: '治安管理', label: '治安管理' },
    { value: '政务服务', label: '政务服务' },
    { value: '其他', label: '其他' }
  ];

  priorityLevels = [
    { value: 1, label: '一般' },
    { value: 2, label: '重要' },
    { value: 3, label: '紧急' }
  ];

  constructor(
    private fb: FormBuilder,
    private ticketService: TicketService,
    private router: Router
  ) {
    this.ticketForm = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(200)]],
      content: ['', [Validators.required, Validators.minLength(20)]],
      category: [''],
      citizenName: ['', [Validators.required, Validators.maxLength(50)]],
      citizenPhone: ['', [Validators.required, Validators.pattern(/^1[3-9]\d{9}$/)]],
      address: ['', [Validators.maxLength(200)]],
      isAnonymous: [false],
      isUrgent: [false],
      priorityLevel: [1]
    });

    this.ticketForm.get('isAnonymous')?.valueChanges.subscribe((isAnonymous) => {
      if (isAnonymous) {
        this.ticketForm.get('citizenName')?.clearValidators();
        this.ticketForm.get('citizenPhone')?.clearValidators();
      } else {
        this.ticketForm.get('citizenName')?.setValidators([Validators.required, Validators.maxLength(50)]);
        this.ticketForm.get('citizenPhone')?.setValidators([Validators.required, Validators.pattern(/^1[3-9]\d{9}$/)]);
      }
      this.ticketForm.get('citizenName')?.updateValueAndValidity();
      this.ticketForm.get('citizenPhone')?.updateValueAndValidity();
    });
  }

  onSubmit(): void {
    if (this.ticketForm.invalid) {
      return;
    }

    this.submitting = true;
    this.error = null;

    const formValue = this.ticketForm.value;
    const ticketDTO: TicketCreateDTO = {
      title: formValue.title,
      content: formValue.content,
      category: formValue.category,
      subCategory: '',
      citizenName: formValue.isAnonymous ? '匿名用户' : formValue.citizenName,
      citizenPhone: formValue.isAnonymous ? '' : formValue.citizenPhone,
      address: formValue.address,
      isAnonymous: formValue.isAnonymous,
      isUrgent: formValue.isUrgent,
      priorityLevel: formValue.priorityLevel,
      departmentId: 0
    };

    this.ticketService.createTicket(ticketDTO).subscribe({
      next: (ticket) => {
        this.createdTicket = ticket;
        this.success = true;
        this.submitting = false;
      },
      error: (err) => {
        this.error = '提交失败，请稍后重试';
        this.submitting = false;
        console.error('Error creating ticket:', err);
      }
    });
  }

  resetForm(): void {
    this.ticketForm.reset({
      title: '',
      content: '',
      category: '',
      citizenName: '',
      citizenPhone: '',
      address: '',
      isAnonymous: false,
      isUrgent: false,
      priorityLevel: 1
    });
    this.success = false;
    this.error = null;
    this.createdTicket = null;
  }
}
