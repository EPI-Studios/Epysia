package fr.epistudio.epysia.net.prediction;

public interface PredictedPhysics {
    PredictedPhysics NONE = new PredictedPhysics() {
        @Override
        public void beginReplay() {
        }

        @Override
        public void stepReplay(float deltaTimeSeconds) {
        }

        @Override
        public void endReplay() {
        }
    };

    void beginReplay();

    void stepReplay(float deltaTimeSeconds);

    void endReplay();
}
