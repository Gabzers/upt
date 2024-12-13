package com.upt.upt.controller;

import com.upt.upt.entity.AssessmentUnit;
import com.upt.upt.entity.CoordinatorUnit;
import com.upt.upt.entity.CurricularUnit;
import com.upt.upt.entity.DirectorUnit;
import com.upt.upt.entity.YearUnit;
import com.upt.upt.entity.SemesterUnit;
import com.upt.upt.service.AssessmentUnitService;
import com.upt.upt.service.CurricularUnitService;
import com.upt.upt.service.CoordinatorUnitService;
import com.upt.upt.service.YearUnitService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;
import java.util.Comparator;

@Controller
@RequestMapping("/coordinator")
public class CurricularUnitController {

    private static final Logger logger = LoggerFactory.getLogger(CurricularUnitController.class);
    private final CurricularUnitService curricularUnitService;
    private final CoordinatorUnitService coordinatorUnitService;
    private final YearUnitService yearUnitService;
    private final AssessmentUnitService assessmentUnitService;

    @Autowired
    public CurricularUnitController(CurricularUnitService curricularUnitService, CoordinatorUnitService coordinatorUnitService, YearUnitService yearUnitService, AssessmentUnitService assessmentUnitService) {
        this.curricularUnitService = curricularUnitService;
        this.coordinatorUnitService = coordinatorUnitService;
        this.yearUnitService = yearUnitService;
        this.assessmentUnitService = assessmentUnitService;
    }

    private Optional<CoordinatorUnit> verifyCoordinator(HttpSession session) {
        Long coordinatorId = (Long) session.getAttribute("userId");
        if (coordinatorId == null) {
            return Optional.empty();
        }
        return coordinatorUnitService.getCoordinatorById(coordinatorId);
    }

    // Mapeamento da URL "/coordinator"
    @GetMapping("")
    public String showCourseList(Model model, HttpSession session) {
        Optional<CoordinatorUnit> coordinatorOpt = verifyCoordinator(session);
        if (coordinatorOpt.isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        CoordinatorUnit coordinator = coordinatorOpt.get();
        List<CurricularUnit> firstSemesterUnits = coordinator.getCurricularUnits().stream()
                .filter(uc -> uc.getSemester() == 1)
                .sorted(Comparator.comparing(CurricularUnit::getYear))
                .collect(Collectors.toList());
        List<CurricularUnit> secondSemesterUnits = coordinator.getCurricularUnits().stream()
                .filter(uc -> uc.getSemester() == 2)
                .sorted(Comparator.comparing(CurricularUnit::getYear))
                .collect(Collectors.toList());
        model.addAttribute("firstSemesterUnits", firstSemesterUnits);
        model.addAttribute("secondSemesterUnits", secondSemesterUnits);
        return "coordinator_index"; // Retorna o nome do arquivo HTML "coordinator_index.html"
    }

    private boolean isUCInMostRecentYear(CurricularUnit uc, YearUnit mostRecentYear) {
        return mostRecentYear.getFirstSemester().getCurricularUnits().contains(uc) ||
               mostRecentYear.getSecondSemester().getCurricularUnits().contains(uc);
    }

    // Remover a UC
    @PostMapping("/remove-uc/{id}")
    public String removeCurricularUnit(@PathVariable("id") Long id, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        curricularUnitService.deleteCurricularUnit(id); // Remove a UC do banco de dados
        return "redirect:/coordinator"; // Redireciona para a lista de UCs
    }

    // Página de edição de UC
    @GetMapping("/coordinator_editUC")
    public String editUC(@RequestParam("id") Long id, @RequestParam("semester") Integer semester, Model model, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        return curricularUnitService.prepareEditUCPage(id, semester, model);
    }

    // Atualizar uma UC
    @PostMapping("/coordinator_editUC/{id}")
    public String updateCurricularUnit(
            @PathVariable("id") Long id,
            @RequestParam("ucName") String nameUC,
            @RequestParam("ucNumStudents") Integer studentsNumber,
            @RequestParam("ucEvaluationType") String evaluationType,
            @RequestParam(value = "ucAttendance", defaultValue = "false") Boolean attendance, // Novo campo
            @RequestParam("ucEvaluationsCount") Integer evaluationsCount,
            @RequestParam("ucYear") Integer year,
            @RequestParam("ucSemester") Integer semester,
            HttpSession session,
            Model model) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        return curricularUnitService.updateCurricularUnit(id, nameUC, studentsNumber, evaluationType, attendance, evaluationsCount, year, semester, session, model);
    }

    // Página de criação de UC
    @GetMapping("/create-uc")
    public String createUC(HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        return "coordinator_createUC"; // Redireciona para a página coordinator_createUC.html
    }

    @GetMapping("/get-semester-id")
    @ResponseBody
    public ResponseEntity<?> getSemesterId(@RequestParam("semester") Integer semester, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return ResponseEntity.status(401).body("Unauthorized access");
        }
        return curricularUnitService.getSemesterId(semester);
    }

    // Criar nova UC
    @PostMapping("/create-uc")
    public String createCurricularUnit(
            @RequestParam("ucName") String nameUC,
            @RequestParam("ucNumStudents") Integer studentsNumber,
            @RequestParam("ucEvaluationType") String evaluationType,
            @RequestParam(value = "ucAttendance", defaultValue = "false") Boolean attendance, // Valor padrão
            @RequestParam("ucEvaluationsCount") Integer evaluationsCount,
            @RequestParam("ucYear") Integer year,
            @RequestParam("ucSemester") Integer semester,
            HttpSession session,
            Model model) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        return curricularUnitService.createCurricularUnit(nameUC, studentsNumber, evaluationType, attendance, evaluationsCount, year, semester, session, model);
    }

    @GetMapping("/coordinator_evaluationsUC")
    public String evaluationsUCCurricular(@RequestParam("id") Long id, Model model, HttpSession session) {
        if (verifyCoordinator(session).isEmpty()) {
            return "redirect:/login?error=Unauthorized access";
        }
        return curricularUnitService.prepareEvaluationsUCPage(id, model);
    }

}
