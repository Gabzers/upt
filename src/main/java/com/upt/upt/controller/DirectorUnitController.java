package com.upt.upt.controller;

import com.upt.upt.entity.CoordinatorUnit;
import com.upt.upt.entity.DirectorUnit;
import com.upt.upt.entity.YearUnit;
import com.upt.upt.service.CoordinatorUnitService;
import com.upt.upt.service.DirectorUnitService;
import com.upt.upt.service.YearUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controller for managing DirectorUnit entities.
 */
@Controller
public class DirectorUnitController {

    @Autowired
    private DirectorUnitService directorUnitService;

    @Autowired
    private CoordinatorUnitService coordinatorService;

    @Autowired
    private YearUnitService yearUnitService;

    @GetMapping("/director")
    public String listDirectors(Model model) {
        List<DirectorUnit> directorUnits = directorUnitService.getAllDirectors();
        model.addAttribute("directorUnits", directorUnits);

        List<CoordinatorUnit> coordinators = coordinatorService.getAllCoordinators();
        model.addAttribute("coordinators", coordinators);

        Optional<YearUnit> currentYear = yearUnitService.getMostRecentYearUnit();
        if (currentYear.isPresent()) {
            model.addAttribute("currentYear", currentYear.get());
            model.addAttribute("mostRecentYear", currentYear.get());
        } else {
            model.addAttribute("currentYear", null);
            model.addAttribute("mostRecentYear", null);
        }

        List<YearUnit> yearUnits = yearUnitService.getAllYearUnits();
        model.addAttribute("yearUnits", yearUnits);

        return "director_index"; // Name of the view template
    }

    @GetMapping("/director/viewSemester/{id}")
    public String viewSemesters(@PathVariable("id") Long id, Model model) {
        Optional<YearUnit> yearUnit = yearUnitService.getYearUnitById(id);
        if (yearUnit.isPresent()) {
            model.addAttribute("yearUnit", yearUnit.get());
            model.addAttribute("firstSemester", yearUnit.get().getFirstSemester());
            model.addAttribute("secondSemester", yearUnit.get().getSecondSemester());
            return "director_viewSemester"; // Name of the view template
        }
        return "redirect:/director?error=Year not found";
    }

    /**
     * Displays the form for creating a new DirectorUnit.
     *
     * @return The name of the view to create a new director
     */
    @GetMapping("/master/create-director")
    public String showCreateDirectorForm() {
        return "master_addDirector"; // The page for adding a director
    }

    /**
     * Creates a new DirectorUnit from the provided form data.
     *
     * @param directorName       The name of the director
     * @param directorDepartment The department of the director
     * @param directorUsername   The username for the director
     * @param directorPassword   The password for the director
     * @return A redirect to the list of master or an error page
     */
    @PostMapping("/master/create-director")
    public String createDirector(@RequestParam("director-name") String directorName,
            @RequestParam("director-department") String directorDepartment,
            @RequestParam("director-username") String directorUsername,
            @RequestParam("director-password") String directorPassword) {
        try {
            DirectorUnit newDirector = new DirectorUnit();
            newDirector.setName(directorName);
            newDirector.setDepartment(directorDepartment);
            newDirector.setUsername(directorUsername);
            newDirector.setPassword(directorPassword);

            directorUnitService.saveDirector(newDirector);
            return "redirect:/master"; // Redirect to the list of master
        } catch (IllegalArgumentException e) {
            return "redirect:/master/create?error=Incomplete information";
        } catch (RuntimeException e) {
            return "redirect:/master/create?error=Duplicate entry or integrity constraint violated";
        }
    }

    /**
     * Displays the form for editing an existing DirectorUnit.
     *
     * @param id    The ID of the director to be edited
     * @param model The model to pass data to the view
     * @return The name of the view to edit the director
     */
    @GetMapping("/master/edit-director")
    public String showEditDirectorForm(@RequestParam("id") Long id, Model model) {
        Optional<DirectorUnit> directorOptional = directorUnitService.getDirectorById(id);
        if (directorOptional.isPresent()) {
            model.addAttribute("director", directorOptional.get());
            return "master_editDirector"; // The page for editing a director
        }
        return "redirect:/master?error=Director not found"; // Redirect if the director is not found
    }

    /**
     * Deletes a DirectorUnit.
     *
     * @param id The ID of the director to be deleted
     * @return A redirect to the list of master
     */
    @PostMapping("/master/remove-director/{id}")
    public String removeDirector(@PathVariable("id") Long id) {
        directorUnitService.deleteDirector(id); // Remove the director from the database
        return "redirect:/master"; // Redirect to the list of directors
    }

    // Página de edição de Diretor
    @GetMapping("/master/director-edit/{id}")
    public String editDirector(@PathVariable("id") Long id, Model model) {
        Optional<DirectorUnit> director = directorUnitService.getDirectorById(id);
        if (director.isPresent()) {
            model.addAttribute("director", director.get()); // Pass the director to the model
            return "director_editCoordinator"; // Return the edit page
        } else {
            return "redirect:/directors"; // Redirect to the list if director not found
        }
    }

    // Atualizar um Diretor
    @PostMapping("/master/director-edit/{id}")
    public String updateDirector(
            @PathVariable("id") Long id,
            @RequestParam("director-name") String name,
            @RequestParam("director-department") String department,
            @RequestParam("director-username") String username,
            @RequestParam(value = "director-password", required = false) String password) {

        try {
            DirectorUnit director = directorUnitService.getDirectorById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Director ID: " + id));

            // Update the director's fields
            director.setName(name);
            director.setDepartment(department); // Assuming `course` maps to `department`
            director.setUsername(username);
            director.setPassword(password != null && !password.isEmpty() ? password : director.getPassword()); // Update
                                                                                                               // password
                                                                                                               // if
                                                                                                               // provided

            // Save the updated director
            directorUnitService.saveDirector(director);

            // Redirect to the list of directors after update
            return "redirect:/master";
        } catch (Exception e) {
            // Log the error and return to the edit page
            e.printStackTrace();
            return "redirect:/director-editCoordinator/" + id + "?error=true";
        }
    }
}
