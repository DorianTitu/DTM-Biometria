# Modelo de datos DMT Biometría

El padrón no utiliza paralelos. Cada estudiante pertenece a un solo curso mediante `students.course_id`.

## Tablas principales

- `courses`: los 13 cursos del colegio.
- `students`: ID único del biométrico, nombres, apellidos, curso y correo del representante. El correo puede ser `NULL`; no se crea una tabla de padres porque el Excel no aporta una entidad reutilizable de representantes.
- `app_users`: credenciales y roles JWT.
- `inspectors`: datos del inspector asociados a un usuario.
- `inspector_assignments`: alcance del inspector. `CURSO` asigna un curso; `GENERAL` cubre todos los cursos. Los índices parciales impiden duplicar responsables activos.
- `course_schedules`: horario de entrada esperado y hora a partir de la que se considera atraso.
- `attendance_events`: historial inmutable recibido desde ZKTeco. Se conserva el ID biométrico aunque el estudiante sea dado de baja.
- `daily_attendance`: resumen diario para el dashboard; se recalcula desde los eventos y permite consultar hoy o los últimos siete días rápidamente.
- `import_runs`: auditoría de cargas Excel append/replace.

## Dashboard del inspector

El backend debe recibir `course_id`, `from` y `to`. Para cada estudiante del curso se cruza el padrón con `daily_attendance`:

- `PRESENT`: existe una primera entrada antes del límite de atraso.
- `LATE`: existe entrada después de `late_after`.
- `ABSENT`: no existe entrada en la fecha consultada.
- `NO_EXIT`: existe entrada, pero no salida cuando el día ya terminó.

La vista semanal consulta un rango inclusivo de siete fechas y nunca modifica los eventos originales.
