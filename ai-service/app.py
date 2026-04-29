from flask import Flask, request, jsonify
from flask_cors import CORS
import jieba
import re
from collections import Counter
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np

app = Flask(__name__)
CORS(app)

DEPARTMENT_CATEGORY_MAP = {
    "城市管理局": ["环境", "卫生", "垃圾", "市容", "绿化", "占道", "违建"],
    "交通运输局": ["交通", "拥堵", "停车", "公交", "地铁", "出租", "违章", "车辆"],
    "教育局": ["学校", "教育", "教师", "学生", "入学", "补课", "学费", "校园"],
    "卫生健康委员会": ["医院", "医疗", "医生", "药品", "医保", "看病", "疫情", "疫苗"],
    "人力资源和社会保障局": ["社保", "养老", "失业", "保险", "公积金", "就业", "工资"],
    "住房和城乡建设局": ["房产", "物业", "拆迁", "安置", "装修", "建筑", "质量", "漏水"],
    "市场监督管理局": ["消费", "价格", "质量", "假货", "投诉", "商家", "工商", "食品"],
    "生态环境局": ["污染", "噪音", "气味", "排放", "环保", "空气", "水", "雾霾"],
    "公安局": ["警察", "盗窃", "诈骗", "治安", "报警", "违法", "犯罪", "案件"],
    "行政审批服务局": ["办证", "审批", "许可", "政务", "流程", "效率", "窗口"],
    "政府服务热线中心": ["咨询", "建议", "其他", "综合"]
}

CATEGORY_MAP = {
    "城市管理": ["环境", "卫生", "垃圾", "市容"],
    "交通出行": ["交通", "拥堵", "停车", "公交", "地铁"],
    "教育资源": ["学校", "教育", "教师", "学生", "入学"],
    "医疗卫生": ["医院", "医疗", "医生", "药品", "医保"],
    "社会保障": ["社保", "养老", "失业", "保险", "公积金"],
    "住房保障": ["房产", "物业", "拆迁", "安置", "装修"],
    "市场监管": ["消费", "价格", "质量", "假货", "投诉"],
    "环境保护": ["污染", "噪音", "气味", "排放", "环保"],
    "治安管理": ["警察", "盗窃", "诈骗", "治安", "报警"],
    "政务服务": ["办证", "审批", "许可", "政务"],
    "其他": ["咨询", "建议", "其他"]
}

KNOWLEDGE_BASE = [
    {
        "id": 1,
        "title": "城市生活垃圾处理管理办法",
        "category": "城市管理",
        "sub_category": "环境卫生",
        "keywords": "垃圾处理,生活垃圾,垃圾分类,清运",
        "summary": "本办法规定了城市生活垃圾的收集、运输、处理等管理要求，明确了相关责任主体和处罚标准。",
        "content": "第一章 总则\n第一条 为加强城市生活垃圾管理，改善城市市容和环境卫生，根据相关法律法规，制定本办法。\n..."
    },
    {
        "id": 2,
        "title": "中华人民共和国道路交通安全法实施条例",
        "category": "交通出行",
        "sub_category": "交通管理",
        "keywords": "交通法规,道路安全,车辆管理,交通事故",
        "summary": "本条例对道路交通安全法的具体实施做出详细规定，包括车辆和驾驶人、道路通行条件、道路通行规定等。",
        "content": "第一章 总则\n第一条 根据《中华人民共和国道路交通安全法》的规定，制定本条例。\n..."
    },
    {
        "id": 3,
        "title": "义务教育法实施细则",
        "category": "教育资源",
        "sub_category": "义务教育",
        "keywords": "义务教育,入学,学费,教师,学校",
        "summary": "本细则详细规定了义务教育的实施步骤，包括入学保障、教学管理、师资队伍建设等方面。",
        "content": "第一章 总则\n第一条 根据中华人民共和国义务教育法，制定本细则。\n..."
    },
    {
        "id": 4,
        "title": "基本医疗保险用药管理暂行办法",
        "category": "医疗卫生",
        "sub_category": "医疗保障",
        "keywords": "医保,用药,药品目录,报销",
        "summary": "本办法规定了基本医疗保险用药的管理规范，包括药品目录制定、支付标准、管理监督等。",
        "content": "第一章 总则\n第一条 为加强基本医疗保险用药管理，保障参保人员基本用药需求，根据相关法律法规，制定本办法。\n..."
    },
    {
        "id": 5,
        "title": "社会保险法实施条例",
        "category": "社会保障",
        "sub_category": "社会保险",
        "keywords": "社保,养老保险,医疗保险,失业保险",
        "summary": "本条例对社会保险法的具体实施做出规定，包括各项社会保险的征缴、待遇、管理监督等。",
        "content": "第一章 总则\n第一条 为了实施《中华人民共和国社会保险法》，制定本条例。\n..."
    }
]

HISTORICAL_TICKETS = [
    {
        "id": 1,
        "ticket_number": "GZ20240115000001",
        "title": "小区附近垃圾站异味严重",
        "content": "我家住在XX小区，附近有一个垃圾中转站，每天早上垃圾车清运时异味非常严重，窗户都不敢开。希望有关部门能够处理一下这个问题。",
        "category": "环境卫生",
        "department": "环境卫生管理处",
        "status": "已办结",
        "created_at": "2024-01-15 08:30:00"
    },
    {
        "id": 2,
        "ticket_number": "GZ20240116000002",
        "title": "XX路交通拥堵问题",
        "content": "XX路早高峰时段交通拥堵严重，建议增加交通信号灯时间或拓宽道路。",
        "category": "交通出行",
        "department": "交通运输局",
        "status": "办理中",
        "created_at": "2024-01-16 09:15:00"
    },
    {
        "id": 3,
        "ticket_number": "GZ20240117000003",
        "title": "入学问题咨询",
        "content": "我家孩子今年要上小学，请问需要准备哪些材料？报名时间是什么时候？",
        "category": "教育资源",
        "department": "教育局",
        "status": "已关闭",
        "created_at": "2024-01-17 14:20:00"
    },
    {
        "id": 4,
        "ticket_number": "GZ20240118000004",
        "title": "医院排队时间过长",
        "content": "XX医院门诊排队时间太长，建议增加窗口或开通网上预约挂号。",
        "category": "医疗卫生",
        "department": "卫生健康委员会",
        "status": "已办结",
        "created_at": "2024-01-18 10:45:00"
    },
    {
        "id": 5,
        "ticket_number": "GZ20240119000005",
        "title": "小区噪音扰民问题",
        "content": "楼下工地夜间施工噪音太大，严重影响居民休息，希望有关部门能够查处。",
        "category": "环境保护",
        "department": "生态环境局",
        "status": "办理中",
        "created_at": "2024-01-19 22:10:00"
    }
]

def preprocess_text(text):
    text = re.sub(r'[^\u4e00-\u9fa5a-zA-Z0-9]', ' ', text)
    words = jieba.lcut(text)
    stopwords = ['的', '了', '是', '在', '有', '和', '与', '或', '等', '这', '那', '我', '你', '他', '们', '就', '也', '都', '要', '会', '能', '可以', '应该', '必须']
    words = [w for w in words if w not in stopwords and len(w.strip()) > 0]
    return ' '.join(words)

def classify_content(content):
    processed_text = preprocess_text(content)
    
    department_scores = {}
    for dept, keywords in DEPARTMENT_CATEGORY_MAP.items():
        score = 0
        for keyword in keywords:
            if keyword in processed_text:
                score += 1
        department_scores[dept] = score
    
    sorted_departments = sorted(department_scores.items(), key=lambda x: x[1], reverse=True)
    best_department = sorted_departments[0][0]
    confidence = 0.7 + (sorted_departments[0][1] * 0.05)
    confidence = min(confidence, 0.95)
    
    category_scores = {}
    for cat, keywords in CATEGORY_MAP.items():
        score = 0
        for keyword in keywords:
            if keyword in processed_text:
                score += 1
        category_scores[cat] = score
    
    sorted_categories = sorted(category_scores.items(), key=lambda x: x[1], reverse=True)
    best_category = sorted_categories[0][0] if sorted_categories[0][1] > 0 else "其他"
    
    words = jieba.lcut(content)
    keywords = [w for w in words if len(w) >= 2][:10]
    
    return {
        "category": best_category,
        "subCategory": "",
        "recommendedDepartment": best_department,
        "recommendedDepartmentId": get_department_id(best_department),
        "confidence": confidence,
        "keywords": keywords
    }

def get_department_id(department_name):
    department_ids = {
        "城市管理局": 1,
        "交通运输局": 2,
        "教育局": 3,
        "卫生健康委员会": 4,
        "人力资源和社会保障局": 5,
        "住房和城乡建设局": 6,
        "市场监督管理局": 7,
        "生态环境局": 8,
        "公安局": 9,
        "行政审批服务局": 10,
        "政府服务热线中心": 11,
        "环境卫生管理处": 12
    }
    return department_ids.get(department_name, 11)

def find_similar_tickets(content, limit=5):
    if not HISTORICAL_TICKETS:
        return []
    
    processed_content = preprocess_text(content)
    
    all_texts = [processed_content]
    all_texts.extend([preprocess_text(t['title'] + ' ' + t['content']) for t in HISTORICAL_TICKETS])
    
    vectorizer = TfidfVectorizer()
    tfidf_matrix = vectorizer.fit_transform(all_texts)
    
    similarities = cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:])[0]
    
    similar_indices = np.argsort(similarities)[::-1][:limit]
    
    results = []
    for idx in similar_indices:
        if similarities[idx] > 0.1:
            ticket = HISTORICAL_TICKETS[idx]
            results.append({
                "ticketId": ticket["id"],
                "ticketNumber": ticket["ticket_number"],
                "title": ticket["title"],
                "similarity": float(similarities[idx]),
                "status": ticket["status"],
                "departmentName": ticket["department"]
            })
    
    return results

def recommend_knowledge(content, limit=5):
    processed_content = preprocess_text(content)
    
    all_texts = [processed_content]
    all_texts.extend([preprocess_text(k['title'] + ' ' + k['keywords'] + ' ' + k['summary']) for k in KNOWLEDGE_BASE])
    
    vectorizer = TfidfVectorizer()
    tfidf_matrix = vectorizer.fit_transform(all_texts)
    
    similarities = cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:])[0]
    
    similar_indices = np.argsort(similarities)[::-1][:limit]
    
    results = []
    for idx in similar_indices:
        if similarities[idx] > 0.05:
            knowledge = KNOWLEDGE_BASE[idx]
            results.append({
                "knowledgeId": knowledge["id"],
                "title": knowledge["title"],
                "category": knowledge["category"],
                "summary": knowledge["summary"],
                "relevanceScore": float(similarities[idx])
            })
    
    return results

@app.route('/api/ai/classify', methods=['POST'])
def classify():
    data = request.get_json()
    content = data.get('content', '')
    
    if not content:
        return jsonify({'error': '内容不能为空'}), 400
    
    result = classify_content(content)
    return jsonify(result)

@app.route('/api/ai/similar', methods=['POST'])
def similar_tickets():
    data = request.get_json()
    content = data.get('content', '')
    limit = data.get('limit', 5)
    ticket_id = data.get('ticket_id')
    
    if not content:
        return jsonify({'error': '内容不能为空'}), 400
    
    results = find_similar_tickets(content, limit)
    
    if ticket_id:
        results = [r for r in results if r['ticketId'] != ticket_id]
    
    return jsonify(results)

@app.route('/api/ai/knowledge', methods=['POST'])
def knowledge():
    data = request.get_json()
    content = data.get('content', '')
    limit = data.get('limit', 5)
    
    if not content:
        return jsonify({'error': '内容不能为空'}), 400
    
    results = recommend_knowledge(content, limit)
    return jsonify(results)

@app.route('/api/ai/batch-assign', methods=['POST'])
def batch_assign():
    data = request.get_json()
    contents = data.get('contents', [])
    
    if not contents:
        return jsonify({'error': '内容列表不能为空'}), 400
    
    results = []
    for content in contents:
        classification = classify_content(content)
        results.append({
            "title": content[:50] + '...' if len(content) > 50 else content,
            "department": classification['recommendedDepartment'],
            "departmentId": classification['recommendedDepartmentId'],
            "confidence": classification['confidence']
        })
    
    return jsonify({
        "totalCount": len(contents),
        "assignedCount": len(results),
        "results": results
    })

@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'service': 'ai-service'})

if __name__ == '__main__':
    jieba.initialize()
    app.run(host='0.0.0.0', port=5000, debug=True)
