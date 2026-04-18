public class Choice {
    private final String prompt;
    private final String optionA;
    private final String optionB;

    public Choice(String prompt, String optionA, String optionB) {
        this.prompt = prompt;
        this.optionA = optionA;
        this.optionB = optionB;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getOptionA() {
        return optionA;
    }

    public String getOptionB() {
        return optionB;
    }
}