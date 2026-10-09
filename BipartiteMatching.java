public class BipartiteMatching {

    private int studentCount;
    private int mentorCount;
    private boolean[][] compatible;
    private int[] mentorCapacity;
    private int totalSlots;
    private int[] slotToMentor;
    private int[] matchOfSlot;
    private boolean[] visitedSlot;

    public int[] studentAssignedMentor;
    public int totalMatched;

    public BipartiteMatching(int studentCount, int mentorCount, boolean[][] compatible, int[] mentorCapacity) {
        this.studentCount = studentCount;
        this.mentorCount = mentorCount;
        this.compatible = compatible;
        this.mentorCapacity = mentorCapacity;
    }

    public void runMatching() {
        totalSlots = 0;
        for (int mentorIndex = 0; mentorIndex < mentorCount; mentorIndex++) {
            totalSlots = totalSlots + mentorCapacity[mentorIndex];
        }

        slotToMentor = new int[totalSlots];
        int slotFillIndex = 0;
        for (int mentorIndex = 0; mentorIndex < mentorCount; mentorIndex++) {
            for (int slotCounter = 0; slotCounter < mentorCapacity[mentorIndex]; slotCounter++) {
                slotToMentor[slotFillIndex] = mentorIndex;
                slotFillIndex++;
            }
        }

        matchOfSlot = new int[totalSlots];
        for (int slotIndex = 0; slotIndex < totalSlots; slotIndex++) {
            matchOfSlot[slotIndex] = -1;
        }

        studentAssignedMentor = new int[studentCount];
        for (int studentIndex = 0; studentIndex < studentCount; studentIndex++) {
            studentAssignedMentor[studentIndex] = -1;
        }

        totalMatched = 0;
        for (int studentIndex = 0; studentIndex < studentCount; studentIndex++) {
            visitedSlot = new boolean[totalSlots];
            if (tryAugment(studentIndex)) {
                totalMatched++;
            }
        }

        for (int slotIndex = 0; slotIndex < totalSlots; slotIndex++) {
            if (matchOfSlot[slotIndex] != -1) {
                int matchedStudent = matchOfSlot[slotIndex];
                studentAssignedMentor[matchedStudent] = slotToMentor[slotIndex];
            }
        }
    }

    private boolean tryAugment(int studentIndex) {
        for (int slotIndex = 0; slotIndex < totalSlots; slotIndex++) {
            int mentorIndex = slotToMentor[slotIndex];
            if (compatible[studentIndex][mentorIndex] && !visitedSlot[slotIndex]) {
                visitedSlot[slotIndex] = true;
                if (matchOfSlot[slotIndex] == -1 || tryAugment(matchOfSlot[slotIndex])) {
                    matchOfSlot[slotIndex] = studentIndex;
                    return true;
                }
            }
        }
        return false;
    }
}
