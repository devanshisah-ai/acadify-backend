import psycopg2
import json
import sys

teacher_id = sys.argv[1]

# 🔥 CONNECT 
conn = psycopg2.connect(
    dbname="acadify",
    user="postgres",
    password="postgres123",
    host="localhost",
    port="5432"
)

cursor = conn.cursor()

# 🔥 GET STUDENTS + MARKS FOR THIS TEACHER
cursor.execute("""
    SELECT st.name, AVG(m.marks_obtained)
    FROM marks m
    JOIN students st ON m.student_id = st.student_id
    JOIN subjects s ON m.subject_id = s.subject_id
    WHERE s.teacher_id = %s
    GROUP BY st.name
""", (teacher_id,))

data = cursor.fetchall()

students = []
weak_students = []

for row in data:
    name = row[0]
    marks = float(row[1]) if row[1] else 0

    students.append({
        "name": name,
        "marks": marks
    })

    if marks < 40:
        weak_students.append(name)

# 🔥 ADD RECOMMENDATION
if len(weak_students) > 0:
    recommendation = "Focus on weak students: " + ", ".join(weak_students)
else:
    recommendation = "Class performance is good"

cursor.close()
conn.close()

# 🔥 FINAL OUTPUT
result = {
    "students": students,
    "weakStudents": weak_students,
    "recommendation": recommendation
}

print(json.dumps(result))