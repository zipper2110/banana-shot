"""Compact definition of the WASB ball-detection network (a small HRNet).

This file is our own code. It follows the layer structure and the
parameter names of models/hrnet.py in https://github.com/nttcom/WASB-SBDT
(MIT license), so that the published tennis weights load with
strict=True. The configuration of configs/model/wasb.yaml is fixed here:
3 frames in (9 channels), 3 heatmaps out, stem 64 channels with stride 1,
stage 1 = 1 bottleneck (32 x 4), stages 2 to 4 = 1 module each with
branches of 16, 32, 64, 128 channels and 2 basic blocks for each branch.
The lab does not run code from the downloaded repository.
"""
import torch
import torch.nn as nn

CH = [16, 32, 64, 128]


def cbr(i, o, stride=1, relu=True, k=3):
    layers = [nn.Conv2d(i, o, k, stride, k // 2, bias=False), nn.BatchNorm2d(o)]
    return nn.Sequential(*(layers + [nn.ReLU(True)] if relu else layers))


class Basic(nn.Module):
    def __init__(self, c):
        super().__init__()
        self.conv1 = nn.Conv2d(c, c, 3, 1, 1, bias=False); self.bn1 = nn.BatchNorm2d(c)
        self.conv2 = nn.Conv2d(c, c, 3, 1, 1, bias=False); self.bn2 = nn.BatchNorm2d(c)

    def forward(self, x):
        y = torch.relu(self.bn1(self.conv1(x)))
        return torch.relu(self.bn2(self.conv2(y)) + x)


class Bottleneck(nn.Module):
    def __init__(self, i, p):
        super().__init__()
        self.conv1 = nn.Conv2d(i, p, 1, bias=False); self.bn1 = nn.BatchNorm2d(p)
        self.conv2 = nn.Conv2d(p, p, 3, 1, 1, bias=False); self.bn2 = nn.BatchNorm2d(p)
        self.conv3 = nn.Conv2d(p, 4 * p, 1, bias=False); self.bn3 = nn.BatchNorm2d(4 * p)
        self.downsample = nn.Sequential(nn.Conv2d(i, 4 * p, 1, bias=False), nn.BatchNorm2d(4 * p))

    def forward(self, x):
        y = torch.relu(self.bn1(self.conv1(x)))
        y = torch.relu(self.bn2(self.conv2(y)))
        return torch.relu(self.bn3(self.conv3(y)) + self.downsample(x))


class Module(nn.Module):
    """One high-resolution module: n parallel branches, then a full fusion."""

    def __init__(self, n):
        super().__init__()
        self.n = n
        self.branches = nn.ModuleList(nn.Sequential(Basic(CH[i]), Basic(CH[i])) for i in range(n))
        fuse = []
        for i in range(n):
            row = []
            for j in range(n):
                if j > i:
                    row.append(nn.Sequential(nn.Conv2d(CH[j], CH[i], 1, bias=False), nn.BatchNorm2d(CH[i]),
                                             nn.Upsample(scale_factor=2 ** (j - i), mode="nearest")))
                elif j == i:
                    row.append(None)
                else:
                    row.append(nn.Sequential(*[cbr(CH[j], CH[i] if k == i - j - 1 else CH[j], 2, relu=k != i - j - 1)
                                               for k in range(i - j)]))
            fuse.append(nn.ModuleList(row))
        self.fuse_layers = nn.ModuleList(fuse)

    def forward(self, x):
        x = [b(v) for b, v in zip(self.branches, x)]
        out = []
        for i in range(self.n):
            y = x[0] if i == 0 else self.fuse_layers[i][0](x[0])
            for j in range(1, self.n):
                y = y + (x[j] if i == j else self.fuse_layers[i][j](x[j]))
            out.append(torch.relu(y))
        return out


class WasbHRNet(nn.Module):
    def __init__(self):
        super().__init__()
        self.conv1 = nn.Conv2d(9, 64, 3, 1, 1, bias=False); self.bn1 = nn.BatchNorm2d(64)
        self.conv2 = nn.Conv2d(64, 64, 3, 1, 1, bias=False); self.bn2 = nn.BatchNorm2d(64)
        self.layer1 = nn.Sequential(Bottleneck(64, 32))
        self.transition1 = nn.ModuleList([cbr(128, 16), nn.Sequential(cbr(128, 32, 2))])
        self.stage2 = nn.Sequential(Module(2))
        self.transition2 = nn.ModuleList([None, None, nn.Sequential(cbr(32, 64, 2))])
        self.stage3 = nn.Sequential(Module(3))
        self.transition3 = nn.ModuleList([None, None, None, nn.Sequential(cbr(64, 128, 2))])
        self.stage4 = nn.Sequential(Module(4))
        self.final_layers = nn.ModuleList([nn.Conv2d(16, 3, 1)])

    def forward(self, x):
        """x: n x 9 x h x w (h, w multiples of 8). Returns n x 3 x h x w logits."""
        x = torch.relu(self.bn1(self.conv1(x)))
        x = torch.relu(self.bn2(self.conv2(x)))
        x = self.layer1(x)
        y = self.stage2([self.transition1[0](x), self.transition1[1](x)])
        y = self.stage3([y[0], y[1], self.transition2[2](y[-1])])
        y = self.stage4([y[0], y[1], y[2], self.transition3[3](y[-1])])
        return self.final_layers[0](y[0])


def load(weights):
    net = WasbHRNet()
    ck = torch.load(weights, map_location="cpu", weights_only=True)   # tensors only, no pickle code
    net.load_state_dict(ck["model_state_dict"], strict=True)
    return net.eval()
