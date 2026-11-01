import { useRef } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/react/daygrid';
import timeGridPlugin from '@fullcalendar/react/timegrid';
import interactionPlugin from '@fullcalendar/react/interaction';
import type { CalendarEvent } from '../types/api';

interface AppointmentCalendarProps {
  events: CalendarEvent[];
  onSelectEvent: (appointmentId: string) => void;
  onDatesSet?: (start: string, end: string) => void;
}

export function AppointmentCalendar({
  events,
  onSelectEvent,
  onDatesSet,
}: AppointmentCalendarProps) {
  const calendarRef = useRef<any>(null);

  const formattedEvents = events.map((e) => {
    let backgroundColor = '#0d9488'; // deep teal
    let borderColor = '#0f766e';

    if (e.status === 'CHECKED_IN') {
      backgroundColor = '#2563eb'; // blue
      borderColor = '#1d4ed8';
    } else if (e.status === 'IN_CONSULTATION') {
      backgroundColor = '#d97706'; // amber
      borderColor = '#b45309';
    } else if (e.status === 'COMPLETED') {
      backgroundColor = '#16a34a'; // green
      borderColor = '#15803d';
    } else if (e.status === 'NO_SHOW') {
      backgroundColor = '#dc2626'; // red
      borderColor = '#b91c1c';
    }

    return {
      id: e.id,
      title: `#${e.tokenNumber} ${e.patientName} (${e.status})`,
      start: e.start,
      end: e.end,
      backgroundColor,
      borderColor,
      textColor: '#ffffff',
      extendedProps: {
        appointmentId: e.id,
        doctorName: e.doctorName,
        mrn: e.patientMrn,
        status: e.status,
      },
    };
  });

  return (
    <div className="rounded-md border bg-surface p-4 shadow-sm calendar-container text-xs">
      <FullCalendar
        ref={calendarRef}
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        initialView="timeGridWeek"
        headerToolbar={{
          left: 'prev,next today',
          center: 'title',
          right: 'dayGridMonth,timeGridWeek,timeGridDay',
        }}
        slotMinTime="08:00:00"
        slotMaxTime="19:00:00"
        allDaySlot={false}
        slotDuration="00:20:00"
        events={formattedEvents}
        eventClick={(info) => {
          info.jsEvent.preventDefault();
          onSelectEvent(info.event.id);
        }}
        datesSet={(dateInfo) => {
          if (onDatesSet) {
            onDatesSet(
              dateInfo.start.toISOString().split('T')[0],
              dateInfo.end.toISOString().split('T')[0]
            );
          }
        }}
        height="auto"
        aspectRatio={1.6}
        nowIndicator
      />
    </div>
  );
}

