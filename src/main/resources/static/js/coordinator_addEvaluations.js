document.addEventListener('DOMContentLoaded', function () {
    const startDateInput = document.getElementById('evaluation-date-start');
    const endDateInput = document.getElementById('evaluation-date-end');
    const examPeriod = document.getElementById('evaluation-exam-period');
    const roomSelect = document.getElementById('evaluation-room');
    const computerRequiredCheckbox = document.getElementById('evaluation-computer-required');

    if (!examPeriod.value) {
        startDateInput.disabled = true;
        endDateInput.disabled = true;
    }

    examPeriod.addEventListener('change', function () {
        if (examPeriod.value) {
            startDateInput.disabled = false;
            endDateInput.disabled = false;
            highlightValidDates();
        } else {
            startDateInput.disabled = true;
            endDateInput.disabled = true;
        }
    });

    endDateInput.addEventListener('change', function () {
        assignRoomAutomatically();
    });

    computerRequiredCheckbox.addEventListener('change', function () {
        resetDateTime();
        assignRoomAutomatically();
    });

    roomSelect.disabled = true;
});

function validateForm() {
    const examPeriod = document.getElementById('evaluation-exam-period').value;
    const startDate = document.getElementById('evaluation-date-start').value;
    const endDate = document.getElementById('evaluation-date-end').value;

    if (!examPeriod) {
        document.getElementById('exam-period-error').style.display = 'inline';
        return false;
    }

    if (!startDate || !endDate) {
        alert('Please select both start and end date and time.');
        return false;
    }

    return true;
}

async function highlightValidDates() {
    const examPeriod = document.getElementById('evaluation-exam-period').value;
    const startDateInput = document.getElementById('evaluation-date-start');
    const endDateInput = document.getElementById('evaluation-date-end');
    const curricularUnitId = document.getElementById('curricular-unit-id').value;

    // Clear existing date & time values
    startDateInput.value = '';
    endDateInput.value = '';

    if (!examPeriod) {
        startDateInput.disabled = true;
        endDateInput.disabled = true;
        return;
    }

    startDateInput.disabled = false;
    endDateInput.disabled = false;

    try {
        const response = await fetch(`/coordinator/getValidDateRanges?examPeriod=${examPeriod}&curricularUnitId=${curricularUnitId}`);
        if (response.ok) {
            const validDateRanges = await response.json();
            const { start, end } = validDateRanges;

            startDateInput.min = start;
            startDateInput.max = end;
            endDateInput.min = start;
            endDateInput.max = end;
        } else {
            console.error('Failed to fetch valid date ranges');
        }
    } catch (error) {
        console.error('Error fetching valid date ranges:', error);
    }
}

function resetDateTime() {
    document.getElementById('evaluation-date-start').value = '';
    document.getElementById('evaluation-date-end').value = '';
}

async function assignRoomAutomatically() {
    const startDateInput = document.getElementById('evaluation-date-start').value;
    const endDateInput = document.getElementById('evaluation-date-end').value;
    const curricularUnitId = document.getElementById('curricular-unit-id').value;
    const computerRequired = document.getElementById('evaluation-computer-required').checked;
    const numStudents = document.getElementById('curricular-unit-students').value;

    if (startDateInput && endDateInput) {
        try {
            const response = await fetch(`/coordinator/getAvailableRooms?startTime=${startDateInput}&endTime=${endDateInput}&curricularUnitId=${curricularUnitId}&computerRequired=${computerRequired}&numStudents=${numStudents}`);
            if (response.ok) {
                const rooms = await response.json();
                const roomSelect = document.getElementById('evaluation-room');
                roomSelect.innerHTML = ''; // Clear existing options
                rooms.forEach(room => {
                    const option = document.createElement('option');
                    option.value = room.id;
                    option.text = `${room.roomNumber} - ${room.building}`;
                    roomSelect.appendChild(option);
                });
                roomSelect.disabled = true;
            } else {
                console.error('Failed to fetch available rooms');
            }
        } catch (error) {
            console.error('Error fetching available rooms:', error);
        }
    }
}

function toggleRoomSelection() {
    const classTime = document.getElementById('evaluation-class-time').checked;
    const roomSelect = document.getElementById('evaluation-room');
    roomSelect.disabled = !classTime;
    if (!classTime) {
        assignRoomAutomatically();
    }
}
