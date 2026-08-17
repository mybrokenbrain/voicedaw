#pragma once
#include <vector>
#include <map>
#include <cmath>
#include <algorithm>
#include <fstream>
#include <string>

namespace voicedaw {

class KnnClassifier {
public:
    KnnClassifier(int k = 3) : mK(k) {}

    void addTrainingData(const std::vector<float>& features, int classId) {
        mTrainingData.push_back({features, classId});
    }

    void clearTrainingData() {
        mTrainingData.clear();
    }
    
    int getNumTrainingSamples() const {
        return mTrainingData.size();
    }

    bool saveToFile(const std::string& path) const {
        std::ofstream out(path, std::ios::binary);
        if (!out) return false;
        size_t numSamples = mTrainingData.size();
        out.write(reinterpret_cast<const char*>(&numSamples), sizeof(numSamples));
        for (const auto& td : mTrainingData) {
            size_t numFeatures = td.features.size();
            out.write(reinterpret_cast<const char*>(&numFeatures), sizeof(numFeatures));
            out.write(reinterpret_cast<const char*>(td.features.data()), numFeatures * sizeof(float));
            out.write(reinterpret_cast<const char*>(&td.classId), sizeof(td.classId));
        }
        return true;
    }

    bool loadFromFile(const std::string& path) {
        std::ifstream in(path, std::ios::binary);
        if (!in) return false;
        mTrainingData.clear();
        size_t numSamples = 0;
        in.read(reinterpret_cast<char*>(&numSamples), sizeof(numSamples));
        for (size_t i = 0; i < numSamples; ++i) {
            size_t numFeatures = 0;
            in.read(reinterpret_cast<char*>(&numFeatures), sizeof(numFeatures));
            std::vector<float> features(numFeatures);
            in.read(reinterpret_cast<char*>(features.data()), numFeatures * sizeof(float));
            int classId = 0;
            in.read(reinterpret_cast<char*>(&classId), sizeof(classId));
            mTrainingData.push_back({features, classId});
        }
        return true;
    }

    int predict(const std::vector<float>& features) {
        if (mTrainingData.empty()) return -1;

        std::vector<std::pair<float, int>> distances;
        distances.reserve(mTrainingData.size());

        for (const auto& td : mTrainingData) {
            float dist = euclideanDistance(features, td.features);
            distances.push_back({dist, td.classId});
        }

        std::sort(distances.begin(), distances.end(), 
            [](const auto& a, const auto& b) { return a.first < b.first; });

        std::map<int, int> votes;
        int k = std::min(mK, (int)distances.size());
        for (int i = 0; i < k; ++i) {
            votes[distances[i].second]++;
        }

        int bestClass = -1;
        int maxVotes = 0;
        for (const auto& v : votes) {
            if (v.second > maxVotes) {
                maxVotes = v.second;
                bestClass = v.first;
            }
        }

        return bestClass;
    }

private:
    float euclideanDistance(const std::vector<float>& a, const std::vector<float>& b) {
        float sum = 0.0f;
        int len = std::min(a.size(), b.size());
        for (int i = 0; i < len; ++i) {
            float d = a[i] - b[i];
            sum += d * d;
        }
        return std::sqrt(sum);
    }

    struct TrainingSample {
        std::vector<float> features;
        int classId;
    };

    int mK;
    std::vector<TrainingSample> mTrainingData;
};

} // namespace voicedaw
