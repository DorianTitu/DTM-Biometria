package ec.dmt.admin.reporting;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ReportControllerTest {
    private final ReportController controller = new ReportController();

    @Test void filtersDemoRecordsAndKeepsSummaryCategoriesDisjoint() {
        LocalDate from=LocalDate.of(2026,9,14);
        var all=controller.attendance(from,from,"","","","");
        assertEquals(64,all.summary().total());
        assertEquals(64,all.summary().present()+all.summary().late()+all.summary().absent());
        assertEquals(16,controller.attendance(from,from,"Octavo EGB","","","").summary().total());
        assertEquals(32,controller.attendance(from,from,"","A","","").summary().total());
        assertTrue(controller.attendance(from,from,"","","ABSENT","").rows().stream().allMatch(r->r.state().equals("ABSENT")));
        assertTrue(controller.attendance(from,from,"","","","Ana").rows().stream().allMatch(r->r.student().contains("Ana")));
    }

    @Test void excludesWeekendsAndRejectsRangesLongerThanNinetyThreeDays() {
        assertTrue(controller.attendance(LocalDate.of(2026,9,12),LocalDate.of(2026,9,13),"","","","").rows().isEmpty());
        var exception=assertThrows(org.springframework.web.server.ResponseStatusException.class,
            ()->controller.attendance(LocalDate.of(2026,1,1),LocalDate.of(2026,4,5),"","","",""));
        assertEquals(400,exception.getStatusCode().value());
    }
}
