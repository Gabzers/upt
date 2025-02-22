package com.upt.upt.controller;

import com.upt.upt.entity.AssessmentUnit;
import com.upt.upt.entity.CurricularUnit;
import com.upt.upt.entity.CoordinatorUnit;
import com.upt.upt.entity.DirectorUnit;
import com.upt.upt.entity.MapUnit;
import com.upt.upt.entity.RoomUnit;
import com.upt.upt.entity.SemesterUnit;
import com.upt.upt.entity.UserType;
import com.upt.upt.entity.YearUnit;
import com.upt.upt.service.AssessmentUnitService;
import com.upt.upt.service.CurricularUnitService;
import com.upt.upt.service.CoordinatorUnitService;
import com.upt.upt.service.RoomUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.LocalTime;

/**
 * Controller for handling requests related to AssessmentUnit entities.
 * 
 * @autor Grupo 5 - 47719, 47713, 46697, 47752, 47004
 */
@Controller
@RequestMapping("/coordinator")
public class AssessmentUnitController {

    @Autowired
    private AssessmentUnitService assessmentUnitService;

    @Autowired
    private CurricularUnitService curricularUnitService;

    @Autowired
    private CoordinatorUnitService coordinatorUnitService;

    @Autowired
    private RoomUnitService roomUnitService;

    private Optional<CoordinatorUnit> verifyCoordinator(HttpSession session) {
        Long coordinatorId = (Long) session.getAttribute("userId");
        if (coordinatorId == null) {
            return Optional.empty();
        }
        return coordinatorUnitService.getCoordinatorById(coordinatorId);
    }

    private boolean isCoordinator(HttpSession session) {
        UserType userType = (UserType) session.getAttribute("userType");
        return userType == UserType.COORDINATOR;
    }

    /**
     * Shows the page to create a new evaluation.
     * 
     * @param curricularUnitId the ID of the curricular unit
     * @param model the model to add attributes to
     * @param error the error message, if any
     * @param session the HTTP session
     * @return the view name
     */
    @GetMapping("/coordinator_create_evaluation")
    public String createEvaluationPage(@RequestParam("curricularUnitId") Long curricularUnitId, Model model,
            @RequestParam(value = "error", required = false) String error, HttpSession session) {
        if (!isCoordinator(session)) {
            return "redirect:/login?error=Unauthorized access";
        }
        Optional<CurricularUnit> curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId);
        if (curricularUnit.isPresent()) {
            CurricularUnit uc = curricularUnit.get();
            if (uc.getEvaluationsCount() == uc.getAssessments().size()) {
                return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId
                        + "&error=Evaluations already complete";
            }
            model.addAttribute("uc", uc);
            model.addAttribute("rooms", roomUnitService.getAllRooms());
            if (error != null) {
                model.addAttribute("error", error);
            }
            return "coordinator_addEvaluations";
        } else {
            return "redirect:/coordinator";
        }
    }

    /**
     * Saves a new evaluation.
     * 
     * @param params the request parameters
     * @param session the HTTP session
     * @param model the model to add attributes to
     * @return the view name
     */
    @PostMapping("/coordinator_addEvaluation")
    public String saveEvaluation(@RequestParam Map<String, String> params, HttpSession session, Model model) {
        if (!isCoordinator(session)) {
            return "redirect:/login?error=Unauthorized access";
        }

        Long curricularUnitId = Long.parseLong(params.get("curricularUnitId"));
        String assessmentExamPeriod = params.get("assessmentExamPeriod");

        if (assessmentExamPeriod == null || assessmentExamPeriod.isEmpty()
                || assessmentExamPeriod.equals("Select Exam Period")) {
            model.addAttribute("error", "Exam Period is required.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=Exam Period is required.";
        }

        Boolean computerRequired = params.get("assessmentComputerRequired") != null;
        Boolean classTime = params.get("assessmentClassTime") != null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String startTimeStr = params.get("assessmentStartTime");
        String endTimeStr = params.get("assessmentEndTime");

        if (startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
            model.addAttribute("error", "Start and end date and time are required.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=Start and end date and time are required.";
        }

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

        // Validate time range
        if (startTime.toLocalTime().isBefore(LocalTime.of(8, 0))
                || endTime.toLocalTime().isAfter(LocalTime.of(23, 59))) {
            model.addAttribute("error", "Assessment times must be between 08:00 and 23:59.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=Assessment times must be between 08:00 and 23:59.";
        }

        // Validate start time is not after or equal to end time
        if (!startTime.isBefore(endTime)) {
            model.addAttribute("error", "Start time cannot be after or equal to end time.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=Start time cannot be after or equal to end time.";
        }

        // Validate interval for specific assessment types
        if (!params.get("assessmentType").equals("Group Work Submission") &&
            !params.get("assessmentType").equals("Work Submission") &&
            !params.get("assessmentType").equals("Work Developed Throughout the Semester") &&
            java.time.Duration.between(startTime, endTime).toHours() > 16) {
            model.addAttribute("error", "Assessments other than 'Group Work Submission', 'Work Submission', and 'Work Developed Throughout the Semester' must not have a duration of more than 16 hours.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=Assessments other than 'Group Work Submission', 'Work Submission', and 'Work Developed Throughout the Semester' must not have a duration of more than 16 hours.";
        }

        Optional<CurricularUnit> curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId);
        if (!curricularUnit.isPresent()) {
            return "redirect:/coordinator";
        }

        CurricularUnit uc = curricularUnit.get();
        if (uc.getEvaluationsCount() == uc.getAssessments().size()) {
            model.addAttribute("error", "Evaluations already complete.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=Evaluations already complete.";
        }

        Long coordinatorId = (Long) session.getAttribute("userId");
        CoordinatorUnit coordinator = coordinatorUnitService.getCoordinatorById(coordinatorId)
                .orElseThrow(() -> new IllegalArgumentException("Coordinator not found"));
        DirectorUnit director = coordinator.getDirectorUnit();
        YearUnit currentYear = director.getCurrentYear();
        SemesterUnit semesterUnit = uc.getSemester() == 1 ? currentYear.getFirstSemester()
                : currentYear.getSecondSemester();

        if (!assessmentUnitService.validateAssessmentDates(assessmentExamPeriod, startTime, endTime, semesterUnit,
                currentYear, model, curricularUnitId)) {
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=Assessment dates must be within the valid period.";
        }

        int periodTotalWeight = assessmentUnitService.calculatePeriodTotalWeight(uc, assessmentExamPeriod,
                Integer.parseInt(params.get("assessmentWeight")));

        if (periodTotalWeight > 100) {
            model.addAttribute("error", "The total weight of evaluations for this period must not exceed 100%.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=The total weight of evaluations for this period must not exceed 100%.";
        }

        if ("Mixed".equals(params.get("ucEvaluationType")) && uc.getAssessments().size() == uc.getEvaluationsCount() - 1
                && !uc.hasExamPeriodEvaluation() && !"Exam Period".equals(assessmentExamPeriod)) {
            model.addAttribute("error",
                    "For Mixed evaluation type, at least one evaluation must be of type 'Exam Period'.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId
                    + "&error=For Mixed evaluation type, at least one evaluation must be of type 'Exam Period'.";
        }

        List<RoomUnit> selectedRooms;
        String assessmentType = params.get("assessmentType");
        if ("Work Developed Throughout the Semester".equals(assessmentType)
                || "Work Submission".equals(assessmentType)
                || "Group Work Submission".equals(assessmentType)) {
            selectedRooms = List.of(roomUnitService.getOrCreateOnlineRoom());
        } else if (classTime) {
            selectedRooms = List.of(roomUnitService.getOrCreateClassTimeRoom());
        } else {
            selectedRooms = roomUnitService.getAvailableRooms(uc.getStudentsNumber(), computerRequired, startTime, endTime);

            if (selectedRooms.isEmpty() || selectedRooms.stream().mapToInt(RoomUnit::getSeatsCount).sum() < uc.getStudentsNumber()) {
                Optional<Map.Entry<LocalDateTime, LocalDateTime>> alternativeTimeSlot = roomUnitService.findAvailableRoomsWithinSameDay(uc.getStudentsNumber(), computerRequired, startTime, endTime);
                if (alternativeTimeSlot.isPresent()) {
                    LocalDateTime newStartTime = alternativeTimeSlot.get().getKey();
                    LocalDateTime newEndTime = alternativeTimeSlot.get().getValue();
                    model.addAttribute("error", "No available rooms found for the specified time. However, rooms are available from " + newStartTime.toLocalTime() + " to " + newEndTime.toLocalTime() + " on the same day.");
                } else {
                    model.addAttribute("error", "No available rooms found for the specified time and day.");
                }
                return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=" + model.getAttribute("error");
            }
        }

        // Validate no overlap with other assessments in the same year/semester within the same coordinator, except for "Work Presentation" and "Group Work Presentation"
        List<AssessmentUnit> assessmentsInSameYearSemester = assessmentUnitService.getAssessmentsByYearAndSemesterAndCoordinator(uc.getYear(), uc.getSemester(), coordinatorId);
        for (AssessmentUnit assessment : assessmentsInSameYearSemester) {
            if ((assessment.getType().equals("Work Presentation") || assessment.getType().equals("Group Work Presentation")) &&
                (params.get("assessmentType").equals("Work Presentation") || params.get("assessmentType").equals("Group Work Presentation")) &&
                assessment.getStartTime().isBefore(endTime.plusHours(24)) && assessment.getEndTime().isAfter(startTime.minusHours(24))) {
                model.addAttribute("error", "Only one 'Work Presentation' or 'Group Work Presentation' can be scheduled within the same 24-hour period.");
                return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Only one 'Work Presentation' or 'Group Work Presentation' can be scheduled within the same 24-hour period.";
            }
            if ((assessment.getType().equals("Work Presentation") || assessment.getType().equals("Group Work Presentation") ||
                params.get("assessmentType").equals("Work Presentation") || params.get("assessmentType").equals("Group Work Presentation")) &&
                (assessment.getStartTime().isEqual(startTime) || assessment.getEndTime().isEqual(endTime) ||
                (startTime.isBefore(assessment.getEndTime()) && endTime.isAfter(assessment.getStartTime())))) {
                model.addAttribute("error", "No other assessments can be scheduled at the same time as a 'Work Presentation' or 'Group Work Presentation'.");
                return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=No other assessments can be scheduled at the same time as a 'Work Presentation' or 'Group Work Presentation'.";
            }
            if (!assessment.getType().equals("Work Presentation") && !assessment.getType().equals("Group Work Presentation") &&
                !params.get("assessmentType").equals("Work Presentation") && !params.get("assessmentType").equals("Group Work Presentation") &&
                assessment.getStartTime().toLocalDate().isEqual(startTime.toLocalDate())) {
                
                // Do not allow the addition of assessments in the same year and semester within the same day
                model.addAttribute("error", "Assessments in the same year and semester cannot be scheduled on the same day.");
                return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Assessments in the same year and semester cannot be scheduled on the same day.";
            }
        }

        // Validate no overlap with other assessments of different years but same coordinator, only if the assessment is on the same day
        List<AssessmentUnit> assessmentsInDifferentYearsSameUC = assessmentUnitService.getAssessmentsByDifferentYearsSameSemesterAndCoordinator(uc.getSemester(), coordinatorId, uc.getYear());
        for (AssessmentUnit assessment : assessmentsInDifferentYearsSameUC) {
            if (!params.get("assessmentType").equals("Work Presentation") && !params.get("assessmentType").equals("Group Work Presentation") &&
                assessment.getStartTime().toLocalDate().isEqual(startTime.toLocalDate())) {
                
                // Check for available slots in the next 24 hours using the same duration as the user-specified time interval
                LocalDateTime nextAvailableStartTime = startTime.plusDays(1).withHour(8).withMinute(0);
                LocalDateTime nextAvailableEndTime = nextAvailableStartTime.plusMinutes(java.time.Duration.between(startTime, endTime).toMinutes());

                boolean slotFound = false;
                while (nextAvailableStartTime.isBefore(startTime.plusDays(2))) {
                    if (roomUnitService.areRoomsAvailable(selectedRooms.stream().map(RoomUnit::getId).collect(Collectors.toList()), nextAvailableStartTime, nextAvailableEndTime)) {
                        slotFound = true;
                        break;
                    }
                    nextAvailableStartTime = nextAvailableStartTime.plusMinutes(30);
                    nextAvailableEndTime = nextAvailableStartTime.plusMinutes(java.time.Duration.between(startTime, endTime).toMinutes());
                }

                if (slotFound) {
                    // Check if there are any assessments on the next day that would conflict
                    boolean conflictOnNextDay = false;
                    for (AssessmentUnit nextDayAssessment : assessmentsInSameYearSemester) {
                        if (nextDayAssessment.getStartTime().toLocalDate().isEqual(nextAvailableStartTime.toLocalDate())) {
                            conflictOnNextDay = true;
                            break;
                        }
                    }
                    for (AssessmentUnit nextDayAssessment : assessmentsInDifferentYearsSameUC) {
                        if (nextDayAssessment.getStartTime().toLocalDate().isEqual(nextAvailableStartTime.toLocalDate())) {
                            conflictOnNextDay = true;
                            break;
                        }
                    }
                    if (!conflictOnNextDay) {
                        model.addAttribute("error", "It is preferable to create the assessment on " + nextAvailableStartTime.toLocalDate() + " from " + nextAvailableStartTime.toLocalTime() + " to " + nextAvailableEndTime.toLocalTime() + " to avoid overlap with other assessments of different years but same course.");
                        return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=It is preferable to create the assessment on " + nextAvailableStartTime.toLocalDate() + " from " + nextAvailableStartTime.toLocalTime() + " to " + nextAvailableEndTime.toLocalTime() + " to avoid overlap with other assessments of different years but same course.";
                    }
                }
            }
        }

        // Validate no overlap with other assessments of the same coordinator
        List<AssessmentUnit> assessmentsByCoordinator = assessmentUnitService.getAssessmentsByCoordinator(coordinatorId);
        for (AssessmentUnit assessment : assessmentsByCoordinator) {
            if (assessment.getStartTime().isBefore(endTime) && assessment.getEndTime().isAfter(startTime)) {
                model.addAttribute("error", "Assessments of the same coordinator cannot overlap.");
                return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Assessments of the same coordinator cannot overlap.";
            }
        }

        AssessmentUnit assessmentUnit = new AssessmentUnit();
        assessmentUnit.setType(params.get("assessmentType"));
        assessmentUnit.setWeight(Integer.parseInt(params.get("assessmentWeight")));
        assessmentUnit.setExamPeriod(assessmentExamPeriod);
        assessmentUnit.setComputerRequired(computerRequired);
        assessmentUnit.setClassTime(classTime);
        assessmentUnit.setStartTime(startTime);
        assessmentUnit.setEndTime(endTime);
        assessmentUnit.setRooms(selectedRooms);
        assessmentUnit.setCurricularUnit(uc);
        assessmentUnit.setMinimumGrade(Double.parseDouble(params.get("assessmentMinimumGrade")));

        // Assign the assessment to the map of the current semester
        MapUnit map = semesterUnit.getMapUnit();
        assessmentUnit.setMap(map);

        assessmentUnitService.saveAssessment(assessmentUnit,
                selectedRooms.stream().map(RoomUnit::getId).collect(Collectors.toList()));

        return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId;
    }

    /**
     * Shows the page to edit an existing evaluation.
     * 
     * @param assessmentId the ID of the assessment
     * @param curricularUnitId the ID of the curricular unit
     * @param model the model to add attributes to
     * @param session the HTTP session
     * @return the view name
     */
    @GetMapping("/coordinator_editEvaluations/{assessmentId}")
    public String editEvaluation(@PathVariable("assessmentId") Long assessmentId,
            @RequestParam("curricularUnitId") Long curricularUnitId, Model model, HttpSession session) {
        if (!isCoordinator(session)) {
            return "redirect:/login?error=Unauthorized access";
        }
        Optional<CurricularUnit> curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId);
        if (curricularUnit.isPresent()) {
            Optional<AssessmentUnit> assessment = assessmentUnitService.getAssessmentByUnitAndId(curricularUnitId,
                    assessmentId);
            if (assessment.isPresent()) {
                AssessmentUnit assessmentUnit = assessment.get();
                model.addAttribute("assessment", assessmentUnit);
                model.addAttribute("formattedStartTime", assessmentUnit.getStartTime().toString());
                model.addAttribute("formattedEndTime", assessmentUnit.getEndTime().toString());
                model.addAttribute("uc", curricularUnit.get());
                return "coordinator_editEvaluations";
            }
        }
        return "redirect:/coordinator";
    }

    /**
     * Updates an existing evaluation.
     * 
     * @param id the ID of the assessment
     * @param params the request parameters
     * @param model the model to add attributes to
     * @param session the HTTP session
     * @return the view name
     */
    @PostMapping("/coordinator_editEvaluations/{id}")
    public String updateEvaluation(@PathVariable("id") Long id, @RequestParam Map<String, String> params,
            Model model, HttpSession session) {
        if (!isCoordinator(session)) {
            return "redirect:/login?error=Unauthorized access";
        }
        Long curricularUnitId = Long.parseLong(params.get("curricularUnitId"));
        String assessmentExamPeriod = params.get("assessmentExamPeriod");
        if (assessmentExamPeriod == null || assessmentExamPeriod.isEmpty()
                || assessmentExamPeriod.equals("Select Exam Period")) {
            model.addAttribute("error", "Exam Period is required.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Exam Period is required.";
        }

        Boolean computerRequired = params.get("assessmentComputerRequired") != null;
        Boolean classTime = params.get("assessmentClassTime") != null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String startTimeStr = params.get("assessmentStartTime");
        String endTimeStr = params.get("assessmentEndTime");

        if (startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
            model.addAttribute("error", "Start and end date and time are required.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Start and end date and time are required.";
        }

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

        // Validate time range
        if (startTime.toLocalTime().isBefore(LocalTime.of(8, 0))
                || endTime.toLocalTime().isAfter(LocalTime.of(23, 59))) {
            model.addAttribute("error", "Assessment times must be between 08:00 and 23:59.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Assessment times must be between 08:00 and 23:59.";
        }

        // Validate start time is not after or equal to end time
        if (!startTime.isBefore(endTime)) {
            model.addAttribute("error", "Start time cannot be after or equal to end time.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Start time cannot be after or equal to end time.";
        }

        // Validate interval for specific assessment types
        if (!params.get("assessmentType").equals("Group Work Submission") &&
            !params.get("assessmentType").equals("Work Submission") &&
            !params.get("assessmentType").equals("Work Developed Throughout the Semester") &&
            java.time.Duration.between(startTime, endTime).toHours() > 16) {
            model.addAttribute("error", "Assessments other than 'Group Work Submission', 'Work Submission', and 'Work Developed Throughout the Semester' must not have a duration of more than 16 hours.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Assessments other than 'Group Work Submission', 'Work Submission', and 'Work Developed Throughout the Semester' must not have a duration of more than 16 hours.";
        }

        Optional<CurricularUnit> curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId);
        if (!curricularUnit.isPresent()) {
            return "redirect:/coordinator";
        }

        CurricularUnit uc = curricularUnit.get();
        Optional<AssessmentUnit> assessmentUnitOptional = assessmentUnitService.findById(id);
        if (!assessmentUnitOptional.isPresent()) {
            return "redirect:/coordinator";
        }

        AssessmentUnit assessmentUnit = assessmentUnitOptional.get();
        int periodTotalWeight = assessmentUnitService.calculatePeriodTotalWeight(uc, assessmentExamPeriod,
                Integer.parseInt(params.get("assessmentWeight"))) - assessmentUnit.getWeight();

        if (periodTotalWeight > 100) {
            model.addAttribute("uc", uc);
            model.addAttribute("assessment", assessmentUnit);
            model.addAttribute("error", "The total weight of evaluations for this period must not exceed 100%.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=The total weight of evaluations for this period must not exceed 100%.";
        }

        boolean updateRooms = !startTime.isEqual(assessmentUnit.getStartTime()) || !endTime.isEqual(assessmentUnit.getEndTime())
                || computerRequired != assessmentUnit.getComputerRequired() || classTime != assessmentUnit.getClassTime()
                || !params.get("assessmentType").equals(assessmentUnit.getType());

        List<RoomUnit> selectedRooms = assessmentUnit.getRooms();
        if (updateRooms) {
            String assessmentType = params.get("assessmentType");
            if ("Work Developed Throughout the Semester".equals(assessmentType)
                    || "Work Submission".equals(assessmentType)
                    || "Group Work Submission".equals(assessmentType)) {
                selectedRooms = List.of(roomUnitService.getOrCreateOnlineRoom());
            } else if (classTime) {
                selectedRooms = List.of(roomUnitService.getOrCreateClassTimeRoom());
            } else {
                selectedRooms = roomUnitService.getAvailableRooms(uc.getStudentsNumber(), computerRequired, startTime, endTime, assessmentUnit.getId());

                if (selectedRooms.isEmpty() || selectedRooms.stream().mapToInt(RoomUnit::getSeatsCount).sum() < uc.getStudentsNumber()) {
                    Optional<Map.Entry<LocalDateTime, LocalDateTime>> alternativeTimeSlot = roomUnitService.findAvailableRoomsWithinSameDay(uc.getStudentsNumber(), computerRequired, startTime, endTime);
                    if (alternativeTimeSlot.isPresent()) {
                        LocalDateTime newStartTime = alternativeTimeSlot.get().getKey();
                        LocalDateTime newEndTime = alternativeTimeSlot.get().getValue();
                        model.addAttribute("error", "No available rooms found for the specified time. However, rooms are available from " + newStartTime.toLocalTime() + " to " + newEndTime.toLocalTime() + " on the same day.");
                    } else {
                        model.addAttribute("error", "No available rooms found for the specified time and day.");
                    }
                    return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=" + model.getAttribute("error");
                }
            }
        }

        // Validate no overlap with other assessments in the same year/semester within the same coordinator, except for "Work Presentation" and "Group Work Presentation"
        List<AssessmentUnit> assessmentsInSameYearSemester = assessmentUnitService.getAssessmentsByYearAndSemesterAndCoordinator(uc.getYear(), uc.getSemester(), (Long) session.getAttribute("userId"));
        for (AssessmentUnit assessment : assessmentsInSameYearSemester) {
            if (assessment.getId().equals(id)) {
                continue; // Skip validation for the same assessment
            }
            if ((assessment.getType().equals("Work Presentation") || assessment.getType().equals("Group Work Presentation")) &&
                (params.get("assessmentType").equals("Work Presentation") || params.get("assessmentType").equals("Group Work Presentation")) &&
                assessment.getStartTime().isBefore(endTime.plusHours(24)) && assessment.getEndTime().isAfter(startTime.minusHours(24))) {
                model.addAttribute("error", "Only one 'Work Presentation' or 'Group Work Presentation' can be scheduled within the same 24-hour period.");
                return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Only one 'Work Presentation' or 'Group Work Presentation' can be scheduled within the same 24-hour period.";
            }
            if ((assessment.getType().equals("Work Presentation") || assessment.getType().equals("Group Work Presentation") ||
                params.get("assessmentType").equals("Work Presentation") || params.get("assessmentType").equals("Group Work Presentation")) &&
                (assessment.getStartTime().isEqual(startTime) || assessment.getEndTime().isEqual(endTime) ||
                (startTime.isBefore(assessment.getEndTime()) && endTime.isAfter(assessment.getStartTime())))) {
                model.addAttribute("error", "No other assessments can be scheduled at the same time as a 'Work Presentation' or 'Group Work Presentation'.");
                return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=No other assessments can be scheduled at the same time as a 'Work Presentation' or 'Group Work Presentation'.";
            }
            if (!assessment.getType().equals("Work Presentation") && !assessment.getType().equals("Group Work Presentation") &&
                !params.get("assessmentType").equals("Work Presentation") && !params.get("assessmentType").equals("Group Work Presentation") &&
                assessment.getStartTime().toLocalDate().isEqual(startTime.toLocalDate())) {
                
                // Do not allow the addition of assessments in the same year and semester within the same day
                model.addAttribute("error", "Assessments in the same year and semester cannot be scheduled on the same day.");
                return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Assessments in the same year and semester cannot be scheduled on the same day.";
            }
        }

        // Validate no overlap with other assessments of different years but same coordinator, only if the assessment is on the same day
        List<AssessmentUnit> assessmentsInDifferentYearsSameUC = assessmentUnitService.getAssessmentsByDifferentYearsSameSemesterAndCoordinator(uc.getSemester(), (Long) session.getAttribute("userId"), uc.getYear());
        for (AssessmentUnit assessment : assessmentsInDifferentYearsSameUC) {
            if (!params.get("assessmentType").equals("Work Presentation") && !params.get("assessmentType").equals("Group Work Presentation") &&
                assessment.getStartTime().toLocalDate().isEqual(startTime.toLocalDate())) {
                
                // Check for available slots in the next 24 hours using the same duration as the user-specified time interval
                LocalDateTime nextAvailableStartTime = startTime.plusDays(1).withHour(8).withMinute(0);
                LocalDateTime nextAvailableEndTime = nextAvailableStartTime.plusMinutes(java.time.Duration.between(startTime, endTime).toMinutes());

                boolean slotFound = false;
                while (nextAvailableStartTime.isBefore(startTime.plusDays(2))) {
                    if (roomUnitService.areRoomsAvailable(selectedRooms.stream().map(RoomUnit::getId).collect(Collectors.toList()), nextAvailableStartTime, nextAvailableEndTime)) {
                        slotFound = true;
                        break;
                    }
                    nextAvailableStartTime = nextAvailableStartTime.plusMinutes(30);
                    nextAvailableEndTime = nextAvailableStartTime.plusMinutes(java.time.Duration.between(startTime, endTime).toMinutes());
                }

                if (slotFound) {
                    // Check if there are any assessments on the next day that would conflict
                    boolean conflictOnNextDay = false;
                    for (AssessmentUnit nextDayAssessment : assessmentsInSameYearSemester) {
                        if (nextDayAssessment.getStartTime().toLocalDate().isEqual(nextAvailableStartTime.toLocalDate())) {
                            conflictOnNextDay = true;
                            break;
                        }
                    }
                    for (AssessmentUnit nextDayAssessment : assessmentsInDifferentYearsSameUC) {
                        if (nextDayAssessment.getStartTime().toLocalDate().isEqual(nextAvailableStartTime.toLocalDate())) {
                            conflictOnNextDay = true;
                            break;
                        }
                    }
                    if (!conflictOnNextDay) {
                        model.addAttribute("error", "It is preferable to create the assessment on " + nextAvailableStartTime.toLocalDate() + " from " + nextAvailableStartTime.toLocalTime() + " to " + nextAvailableEndTime.toLocalTime() + " to avoid overlap with other assessments of different years but same course.");
                        return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=It is preferable to create the assessment on " + nextAvailableStartTime.toLocalDate() + " from " + nextAvailableStartTime.toLocalTime() + " to " + nextAvailableEndTime.toLocalTime() + " to avoid overlap with other assessments of different years but same course.";
                    }
                }
            }
        }

        // Validate no overlap with other assessments of the same coordinator
        List<AssessmentUnit> assessmentsByCoordinator = assessmentUnitService.getAssessmentsByCoordinator((Long) session.getAttribute("userId"));
        for (AssessmentUnit assessment : assessmentsByCoordinator) {
            if (assessment.getId().equals(id)) {
                continue; // Skip validation for the same assessment
            }
            if (assessment.getStartTime().isBefore(endTime) && assessment.getEndTime().isAfter(startTime)) {
                model.addAttribute("error", "Assessments of the same coordinator cannot overlap.");
                return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId + "&error=Assessments of the same coordinator cannot overlap.";
            }
        }

        assessmentUnit.setType(params.get("assessmentType"));
        assessmentUnit.setWeight(Integer.parseInt(params.get("assessmentWeight")));
        assessmentUnit.setExamPeriod(assessmentExamPeriod);
        assessmentUnit.setComputerRequired(computerRequired);
        assessmentUnit.setClassTime(classTime);
        assessmentUnit.setStartTime(startTime);
        assessmentUnit.setEndTime(endTime);
        if (updateRooms) {
            assessmentUnit.setRooms(selectedRooms);
        }
        assessmentUnit.setMinimumGrade(Double.parseDouble(params.get("assessmentMinimumGrade")));

        assessmentUnitService.updateAssessment(id, assessmentUnit, selectedRooms.stream().map(RoomUnit::getId).collect(Collectors.toList()));

        return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId;
    }

    /**
     * Shows the confirmation page to delete an assessment.
     * 
     * @param id the ID of the assessment
     * @param curricularUnitId the ID of the curricular unit
     * @param model the model to add attributes to
     * @param session the HTTP session
     * @return the view name
     */
    @PostMapping("/delete-assessment/{id}")
    public String deleteAssessment(@PathVariable("id") Long id, @RequestParam("curricularUnitId") Long curricularUnitId,
            Model model, HttpSession session) {
        if (!isCoordinator(session)) {
            return "redirect:/login?error=Unauthorized access";
        }
        model.addAttribute("warning", "Are you sure you want to remove this assessment?");
        model.addAttribute("assessmentId", id);
        model.addAttribute("curricularUnitId", curricularUnitId);
        return "coordinator_confirmRemoveAssessment";
    }

    /**
     * Confirms the removal of an assessment.
     * 
     * @param id the ID of the assessment
     * @param curricularUnitId the ID of the curricular unit
     * @param session the HTTP session
     * @return the view name
     */
    @PostMapping("/confirm-remove-assessment/{id}")
    public String confirmRemoveAssessment(@PathVariable("id") Long id,
            @RequestParam("curricularUnitId") Long curricularUnitId, HttpSession session) {
        if (!isCoordinator(session)) {
            return "redirect:/login?error=Unauthorized access";
        }
        assessmentUnitService.deleteAssessment(curricularUnitId, id);
        return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId;
    }

    /**
     * Deletes an evaluation.
     * 
     * @param id the ID of the assessment
     * @param curricularUnitId the ID of the curricular unit
     * @param session the HTTP session
     * @return the view name
     */
    @PostMapping("/coordinator_delete_evaluation/{id}")
    public String deleteEvaluation(@PathVariable("id") Long id, @RequestParam("curricularUnitId") Long curricularUnitId,
            HttpSession session) {
        if (!isCoordinator(session)) {
            return "redirect:/login?error=Unauthorized access";
        }
        assessmentUnitService.deleteAssessment(curricularUnitId, id);
        return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId;
    }

    /**
     * Gets the valid date ranges for an exam period.
     * 
     * @param examPeriod the exam period
     * @param curricularUnitId the ID of the curricular unit
     * @param session the HTTP session
     * @return the valid date ranges
     */
    @GetMapping("/getValidDateRanges")
    @ResponseBody
    public Map<String, String> getValidDateRanges(@RequestParam("examPeriod") String examPeriod,
            @RequestParam("curricularUnitId") Long curricularUnitId, HttpSession session) {
        if (!isCoordinator(session)) {
            throw new IllegalArgumentException("Unauthorized access");
        }

        CoordinatorUnit coordinator = verifyCoordinator(session)
                .orElseThrow(() -> new IllegalArgumentException("Coordinator not found"));
        DirectorUnit director = coordinator.getDirectorUnit();
        YearUnit currentYear = director.getCurrentYear();

        CurricularUnit curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Curricular Unit not found"));

        return assessmentUnitService.getValidDateRanges(examPeriod, curricularUnit, currentYear);
    }

    /**
     * Gets the available rooms for a given time range and requirements.
     * 
     * @param startTimeStr the start time
     * @param endTimeStr the end time
     * @param computerRequired whether a computer is required
     * @param numStudents the number of students
     * @param session the HTTP session
     * @return the available rooms
     */
    @GetMapping("/availableRooms")
    @ResponseBody
    public List<RoomUnit> getAvailableRooms(@RequestParam("startTime") String startTimeStr,
            @RequestParam("endTime") String endTimeStr, @RequestParam("computerRequired") boolean computerRequired,
            @RequestParam("numStudents") int numStudents, HttpSession session) {
        if (!isCoordinator(session)) {
            throw new IllegalArgumentException("Unauthorized access");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

        return roomUnitService.getAvailableRooms(numStudents, computerRequired, startTime, endTime);
    }
}
