import psycopg2
import json
import sys

student_id = sys.argv[1]

conn = psycopg2.connect(
    dbname="acadify",
    user="postgres",
    password="postgres123",
    host="localhost",
    port="5432"
)
cursor = conn.cursor()

# ── 1. SEMESTER TREND ──────────────────────────────────────────────────────
cursor.execute("""
    SELECT semester, AVG(marks_obtained) as avg_marks
    FROM marks
    WHERE student_id = %s
    GROUP BY semester
    ORDER BY semester
""", (student_id,))

trend_data = cursor.fetchall()
semesterTrend = [
    {"semester": row[0], "cgpa": float(row[1]) / 10}
    for row in trend_data
]

# ── 2. SUBJECT-WISE MARKS (current vs previous semester) ──────────────────
# Get the two most recent semesters this student has marks for
cursor.execute("""
    SELECT DISTINCT semester FROM marks
    WHERE student_id = %s
    ORDER BY semester DESC
    LIMIT 2
""", (student_id,))
semesters = [row[0] for row in cursor.fetchall()]

current_sem   = semesters[0] if len(semesters) > 0 else None
previous_sem  = semesters[1] if len(semesters) > 1 else None

# Current semester marks per subject
cursor.execute("""
    SELECT s.subject_name, AVG(m.marks_obtained)
    FROM marks m
    JOIN subjects s ON m.subject_id = s.subject_id
    WHERE m.student_id = %s AND m.semester = %s
    GROUP BY s.subject_name
""", (student_id, current_sem))
current_marks = {row[0]: float(row[1]) for row in cursor.fetchall()}

# Previous semester marks per subject
previous_marks = {}
if previous_sem:
    cursor.execute("""
        SELECT s.subject_name, AVG(m.marks_obtained)
        FROM marks m
        JOIN subjects s ON m.subject_id = s.subject_id
        WHERE m.student_id = %s AND m.semester = %s
        GROUP BY s.subject_name
    """, (student_id, previous_sem))
    previous_marks = {row[0]: float(row[1]) for row in cursor.fetchall()}

# ── 3. ALL SUBJECTS (for overview cards) ──────────────────────────────────
cursor.execute("""
    SELECT s.subject_name, AVG(m.marks_obtained)
    FROM marks m
    JOIN subjects s ON m.subject_id = s.subject_id
    WHERE m.student_id = %s
    GROUP BY s.subject_name
""", (student_id,))
all_subjects_data = cursor.fetchall()
subjects = [{"name": row[0], "marks": float(row[1])} for row in all_subjects_data]

# ── 4. PER-SUBJECT TREND MESSAGES ─────────────────────────────────────────
subjectTrends = []
DECLINE_THRESHOLD = 5   # marks drop > 5 = declined
IMPROVE_THRESHOLD = 5   # marks rise > 5 = improved

for subject, curr in current_marks.items():
    prev = previous_marks.get(subject)

    if prev is None:
        # No previous data — just report current
        trend = "new"
        if curr >= 75:
            message = f"Great start in {subject}!"
        elif curr >= 50:
            message = f"{subject} is looking okay so far."
        else:
            message = f"Needs more attention in {subject}."
    elif curr - prev > IMPROVE_THRESHOLD:
        trend = "improved"
        message = f"Best performance in {subject} — keep it up!"
    elif prev - curr > DECLINE_THRESHOLD:
        trend = "declined"
        message = f"Your performance in {subject} declined from last time."
    else:
        trend = "stable"
        message = f"{subject} was okay this time."

    subjectTrends.append({
        "subject": subject,
        "currentMarks": round(curr, 1),
        "previousMarks": round(prev, 1) if prev is not None else None,
        "trend": trend,
        "message": message
    })

# Sort: declined first, then stable, then improved
trend_order = {"declined": 0, "stable": 1, "improved": 2, "new": 3}
subjectTrends.sort(key=lambda x: trend_order.get(x["trend"], 3))

# ── 5. STRONG / WEAK SUBJECTS ─────────────────────────────────────────────
weakSubjects   = [s["name"] for s in subjects if s["marks"] < 50]
strongSubjects = [s["name"] for s in subjects if s["marks"] >= 75]

# Best and worst subject from current semester
best_subject  = max(current_marks, key=current_marks.get) if current_marks else None
worst_subject = min(current_marks, key=current_marks.get) if current_marks else None

# ── 6. OVERALL RECOMMENDATION ─────────────────────────────────────────────
avg = sum(current_marks.values()) / len(current_marks) if current_marks else 0

if avg >= 75:
    recommendation = "Excellent performance! Maintain consistency across all subjects."
elif avg >= 60:
    recommendation = "Good performance. Focus on improving weaker subjects."
elif avg >= 40:
    recommendation = "Average performance. Dedicate more time to subjects that declined."
else:
    recommendation = "Performance needs improvement. Seek help for subjects you are struggling with."

cursor.close()
conn.close()

# ── FINAL OUTPUT ──────────────────────────────────────────────────────────
result = {
    "semesterTrend":  semesterTrend,
    "subjects":       subjects,
    "subjectTrends":  subjectTrends,
    "weakSubjects":   weakSubjects,
    "strongSubjects": strongSubjects,
    "bestSubject":    best_subject,
    "worstSubject":   worst_subject,
    "recommendation": recommendation
}

print(json.dumps(result))