# Marcaciones y deduplicación

Cada lectura recibida se guarda en `attendance_events`. Es el registro de auditoría y puede contener lecturas repetidas.

La asistencia visible se consolida por la clave `estudiante + fecha local de Ecuador + tipo de evento`:

- La primera `ENTRY` del día fija `daily_attendance.first_entry_at` y determina `PRESENT` o `LATE` según el horario del curso.
- La primera `EXIT` del día fija `daily_attendance.last_exit_at`.
- Lecturas posteriores del mismo tipo se conservan como eventos crudos, pero no cambian la marcación visible ni generan otra notificación.
- Cuando inicia un día no se copian registros: el dashboard hace un cruce entre el padrón activo y `daily_attendance`. Si no hay entrada para esa fecha, muestra `ABSENT` o “Pendiente”.

## Correos

La futura tarea de envío debe consumir solamente `attendance_notifications` con estado `PENDING`. La restricción única `(attendance_date, student_id, event_type)` impide que una segunda lectura del mismo estudiante y día cree un segundo correo, incluso si llega desde otro dispositivo.

El envío todavía no está implementado. Al completarlo, el proceso deberá actualizar el estado de la cola a `SENT` o `FAILED`, sin volver a insertar una nueva fila.
