package com.upt.upt.controller;

import com.upt.upt.entity.AssessmentUnit;
import com.upt.upt.entity.CurricularUnit;
import com.upt.upt.entity.CoordinatorUnit;
import com.upt.upt.entity.DirectorUnit;
import com.upt.upt.entity.MapUnit;
import com.upt.upt.entity.RoomUnit;
import com.upt.upt.entity.SemesterUnit;
import com.upt.upt.entity.YearUnit;
import com.upt.upt.service.AssessmentUnitService;
import com.upt.upt.service.CurricularUnitService;
import com.upt.upt.service.CoordinatorUnitService;
import com.upt.upt.service.RoomUnitService;
import com.upt.upt.service.YearUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for handling requests related to AssessmentUnit entities.
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
    private YearUnitService yearUnitService;

    @Autowired
    private RoomUnitService roomUnitService;

    private Optional<CoordinatorUnit> verifyCoordinator(HttpSession session) {
        Long coordinatorId = (Long) session.getAttribute("userId");
        if (coordinatorId == null) {
            return Optional.empty();
        }
        return coordinatorUnitService.getCoordinatorById(coordinatorId);
    }

    @GetMapping("/coordinator_create_evaluation")
    public String createEvaluationPage(@RequestParam("curricularUnitId") Long curricularUnitId, Model model, @RequestParam(value = "error", required = false) String error, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        Optional<CurricularUnit> curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId);
        if (curricularUnit.isPresent()) {
            CurricularUnit uc = curricularUnit.get();
            if (uc.getEvaluationsCount() == uc.getAssessments().size()) {
                return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId + "&error=Evaluations already complete";
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

    @PostMapping("/coordinator_addEvaluation")
    public String saveEvaluation(@RequestParam Map<String, String> params, HttpSession session, Model model) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        Long curricularUnitId = Long.parseLong(params.get("curricularUnitId"));
        String assessmentExamPeriod = params.get("assessmentExamPeriod");
        if (assessmentExamPeriod == null || assessmentExamPeriod.isEmpty() || assessmentExamPeriod.equals("Select Exam Period")) {
            model.addAttribute("error", "Exam Period is required.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Exam Period is required.";
        }

        Boolean computerRequired = params.get("assessmentComputerRequired") != null;
        Boolean classTime = params.get("assessmentClassTime") != null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String startTimeStr = params.get("assessmentStartTime");
        String endTimeStr = params.get("assessmentEndTime");

        if (startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
            model.addAttribute("error", "Start and end date and time are required.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Start and end date and time are required.";
        }

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

        Optional<CurricularUnit> curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId);
        if (!curricularUnit.isPresent()) {
            return "redirect:/coordinator";
        }

        CurricularUnit uc = curricularUnit.get();
        if (uc.getEvaluationsCount() == uc.getAssessments().size()) {
            model.addAttribute("error", "Evaluations already complete.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Evaluations already complete.";
        }

        Long coordinatorId = (Long) session.getAttribute("userId");
        CoordinatorUnit coordinator = coordinatorUnitService.getCoordinatorById(coordinatorId)
                .orElseThrow(() -> new IllegalArgumentException("Coordinator not found"));
        DirectorUnit director = coordinator.getDirectorUnit();
        YearUnit currentYear = director.getCurrentYear();
        SemesterUnit semesterUnit = uc.getSemester() == 1 ? currentYear.getFirstSemester() : currentYear.getSecondSemester();

        if (!assessmentUnitService.validateAssessmentDates(assessmentExamPeriod, startTime, endTime, semesterUnit, currentYear, model, curricularUnitId)) {
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Assessment dates must be within the valid period.";
        }

        int periodTotalWeight = assessmentUnitService.calculatePeriodTotalWeight(uc, assessmentExamPeriod, Integer.parseInt(params.get("assessmentWeight")));

        if (periodTotalWeight > 100) {
            model.addAttribute("error", "The total weight of evaluations for this period must not exceed 100%.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=The total weight of evaluations for this period must not exceed 100%.";
        }

        if ("Mixed".equals(params.get("ucEvaluationType")) && uc.getAssessments().size() == uc.getEvaluationsCount() - 1 && !uc.hasExamPeriodEvaluation() && !"Exam Period".equals(assessmentExamPeriod)) {
            model.addAttribute("error", "For Mixed evaluation type, at least one evaluation must be of type 'Exam Period'.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=For Mixed evaluation type, at least one evaluation must be of type 'Exam Period'.";
        }

        // Fetch available rooms based on the number of students and computer requirement
        List<RoomUnit> availableRooms = new ArrayList<>(roomUnitService.getAvailableRooms(uc.getStudentsNumber(), computerRequired, startTime, endTime));
        if (availableRooms.isEmpty()) {
            model.addAttribute("error", "No available rooms found for the specified criteria.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=No available rooms found for the specified criteria.";
        }

        // Sort rooms by the number of seats closest to the number of students
        availableRooms.sort((r1, r2) -> {
            int diff1 = Math.abs(r1.getSeatsCount() - uc.getStudentsNumber());
            int diff2 = Math.abs(r2.getSeatsCount() - uc.getStudentsNumber());
            return Integer.compare(diff1, diff2);
        });

        RoomUnit room = null;
        for (RoomUnit availableRoom : availableRooms) {
            if (assessmentUnitService.isRoomAvailable(availableRoom.getId(), startTime, endTime)) {
                room = availableRoom;
                break;
            }
        }

        if (room == null) {
            List<RoomUnit> allRooms = new ArrayList<>(roomUnitService.getAllRooms());
            allRooms.sort((r1, r2) -> {
                int diff1 = Math.abs(r1.getSeatsCount() - uc.getStudentsNumber());
                int diff2 = Math.abs(r2.getSeatsCount() - uc.getStudentsNumber());
                return Integer.compare(diff1, diff2);
            });
            for (RoomUnit availableRoom : allRooms) {
                if (assessmentUnitService.isRoomAvailable(availableRoom.getId(), startTime, endTime)) {
                    room = availableRoom;
                    break;
                }
            }
        }

        if (room == null) {
            model.addAttribute("error", "No available rooms found for the specified time.");
            return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=No available rooms found for the specified time.";
        }

        // Validate no overlap with other assessments in the same year/semester within the same coordinator
        List<AssessmentUnit> assessmentsInSameYearSemester = assessmentUnitService.getAssessmentsByYearAndSemesterAndCoordinator(uc.getYear(), uc.getSemester(), coordinatorId);
        for (AssessmentUnit assessment : assessmentsInSameYearSemester) {
            if (!assessment.getType().equals("Work Presentation") && assessment.getStartTime().isBefore(endTime.plusHours(24)) && assessment.getEndTime().isAfter(startTime.minusHours(24))) {
                model.addAttribute("error", "There must be at least 24 hours between assessments in the same year/semester.");
                return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=There must be at least 24 hours between assessments in the same year/semester.";
            }
        }

        // Validate no overlap with other assessments of different years but same UC within the same coordinator
        List<AssessmentUnit> assessmentsInDifferentYearsSameUC = assessmentUnitService.getAssessmentsByDifferentYearsSameSemesterAndCoordinator(uc.getSemester(), coordinatorId, uc.getYear());
        for (AssessmentUnit assessment : assessmentsInDifferentYearsSameUC) {
            if (assessment.getStartTime().isBefore(endTime.plusHours(24)) && assessment.getEndTime().isAfter(startTime.minusHours(24))) {
                model.addAttribute("error", "Avoid scheduling assessments of different years but same UC on overlapping dates.");
                return "redirect:/coordinator/coordinator_create_evaluation?curricularUnitId=" + curricularUnitId + "&error=Avoid scheduling assessments of different years but same UC on overlapping dates.";
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
        assessmentUnit.setRoom(room);
        assessmentUnit.setCurricularUnit(uc);
        assessmentUnit.setMinimumGrade(Double.parseDouble(params.get("assessmentMinimumGrade")));

        // Assign the assessment to the map of the current semester
        MapUnit map = semesterUnit.getMapUnit();
        assessmentUnit.setMap(map);

        assessmentUnitService.saveAssessment(assessmentUnit);

        return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId;
    }

    @GetMapping("/coordinator_editEvaluations/{assessmentId}")
    public String editEvaluation(@PathVariable("assessmentId") Long assessmentId, @RequestParam("curricularUnitId") Long curricularUnitId, Model model, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        Optional<CurricularUnit> curricularUnit = curricularUnitService.getCurricularUnitById(curricularUnitId);
        if (curricularUnit.isPresent()) {
            Optional<AssessmentUnit> assessment = assessmentUnitService.getAssessmentByUnitAndId(curricularUnitId, assessmentId);
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

    @PostMapping("/coordinator_editEvaluations/{id}")
    public String updateEvaluation(@PathVariable("id") Long id, @RequestParam Map<String, String> params, Model model, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        Long curricularUnitId = Long.parseLong(params.get("curricularUnitId"));
        String assessmentExamPeriod = params.get("assessmentExamPeriod");
        if (assessmentExamPeriod == null || assessmentExamPeriod.isEmpty() || assessmentExamPeriod.equals("Select Exam Period")) {
            model.addAttribute("error", "Exam Period is required.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId;
        }

        Boolean computerRequired = params.get("assessmentComputerRequired") != null;
        Boolean classTime = params.get("assessmentClassTime") != null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String startTimeStr = params.get("assessmentStartTime");
        String endTimeStr = params.get("assessmentEndTime");

        if (startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
            model.addAttribute("error", "Start and end date and time are required.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId;
        }

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

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
        int periodTotalWeight = assessmentUnitService.calculatePeriodTotalWeight(uc, assessmentExamPeriod, Integer.parseInt(params.get("assessmentWeight"))) - assessmentUnit.getWeight();

        if (periodTotalWeight > 100) {
            model.addAttribute("uc", uc);
            model.addAttribute("assessment", assessmentUnit);
            model.addAttribute("error", "The total weight of evaluations for this period must not exceed 100%.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId;
        }

        RoomUnit room = roomUnitService.getRoomById(Long.parseLong(params.get("assessmentRoomId")));
        if (room == null) {
            model.addAttribute("error", "Room not found.");
            return "redirect:/coordinator/coordinator_editEvaluations/" + id + "?curricularUnitId=" + curricularUnitId;
        }

        assessmentUnit.setType(params.get("assessmentType"));
        assessmentUnit.setWeight(Integer.parseInt(params.get("assessmentWeight")));
        assessmentUnit.setExamPeriod(assessmentExamPeriod);
        assessmentUnit.setComputerRequired(computerRequired);
        assessmentUnit.setClassTime(classTime);
        assessmentUnit.setStartTime(startTime);
        assessmentUnit.setEndTime(endTime);
        assessmentUnit.setRoom(room);
        assessmentUnit.setMinimumGrade(Double.parseDouble(params.get("assessmentMinimumGrade")));

        assessmentUnitService.saveAssessment(assessmentUnit);

        return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId;
    }

    @PostMapping("/coordinator_delete_evaluation/{id}")
    public String deleteEvaluation(@PathVariable("id") Long id, @RequestParam("curricularUnitId") Long curricularUnitId, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        assessmentUnitService.deleteAssessment(curricularUnitId, id);
        return "redirect:/coordinator/coordinator_evaluationsUC?id=" + curricularUnitId;
    }

    @GetMapping("/getValidDateRanges")
    @ResponseBody
    public Map<String, String> getValidDateRanges(@RequestParam("examPeriod") String examPeriod, @RequestParam("curricularUnitId") Long curricularUnitId, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
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

    @GetMapping("/availableRooms")
    @ResponseBody
    public List<RoomUnit> getAvailableRooms(@RequestParam("startTime") String startTimeStr, @RequestParam("endTime") String endTimeStr, @RequestParam("computerRequired") boolean computerRequired, @RequestParam("numStudents") int numStudents, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            throw new IllegalArgumentException("Unauthorized access");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

        return roomUnitService.getAvailableRooms(numStudents, computerRequired, startTime, endTime);
    }
}
